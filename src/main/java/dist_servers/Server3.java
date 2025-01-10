package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.MessageProto.Message;
import com.hasup.proto.SubscriberProto.Status;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;

public class Server3 implements ServerInterface {
    private final int SERVER_ID = 3;
    private final int ADMIN_PORT = 7003;
    private final int CLIENT_PORT = 6003;
    private final int PEER_PORT = 5003;
    
    private Configuration config;
    private final ConcurrentHashMap<Integer, Subscriber> subscribers;
    private final ReentrantLock subscriberLock;
    private volatile boolean isRunning;
    private final ConcurrentHashMap<Integer, Socket> peerConnections;
    
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public Server3() {
        this.subscribers = new ConcurrentHashMap<>();
        this.subscriberLock = new ReentrantLock();
        this.peerConnections = new ConcurrentHashMap<>();
    }
    
    public void start() {
        // Başlangıçta sadece admin portu dinle
        CompletableFuture.runAsync(() -> {
            try (ServerSocket adminSocket = new ServerSocket(ADMIN_PORT)) {
                System.out.println("Server" + SERVER_ID + " admin bağlantıları için dinleniyor: " + ADMIN_PORT);
                while (true) {
                    Socket socket = adminSocket.accept();
                    new Thread(new AdminHandler(socket, this)).start();
                }
            } catch (IOException e) {
                System.err.println("Admin socket hatası: " + e.getMessage());
            }
        });
    }
    
    public void startServices() {
        if (!isRunning) {
            this.isRunning = true;
            
            // Önce peer portunu aç
            CompletableFuture.runAsync(() -> {
                try (ServerSocket peerSocket = new ServerSocket(PEER_PORT)) {
                    System.out.println("Server" + SERVER_ID + " peer bağlantıları için dinleniyor: " + PEER_PORT);
                    // Server1 ve Server2'den gelen bağlantıları bekle
                    while (isRunning && peerConnections.size() < 2) {
                        Socket socket = peerSocket.accept();
                        handlePeerConnection(socket);
                    }
                } catch (IOException e) {
                    System.err.println("Peer socket hatası: " + e.getMessage());
                }
            });

            // Diğer sunuculara bağlan
            connectToPeers();
            
            // Client bağlantıları için thread
            CompletableFuture.runAsync(() -> {
                try (ServerSocket clientSocket = new ServerSocket(CLIENT_PORT)) {
                    System.out.println("Server" + SERVER_ID + " client bağlantıları için dinleniyor: " + CLIENT_PORT);
                    while (isRunning) {
                        Socket socket = clientSocket.accept();
                        new Thread(new ClientHandler(socket, this)).start();
                    }
                } catch (IOException e) {
                    System.err.println("Client socket hatası: " + e.getMessage());
                }
            });

            System.out.println("Server" + SERVER_ID + " servisleri başlatıldı");
        }
    }
    
    public void stop() {
        this.isRunning = false;
        shutdownLatch.countDown();
    }
    
    public void waitForShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public int getServerId() {
        return SERVER_ID;
    }
    
    @Override
    public Capacity getCapacity() {
        return Capacity.newBuilder()
            .setServerId(SERVER_ID)
            .setServerStatus(subscribers.size())
            .setTimestamp(System.currentTimeMillis())
            .build();
    }
    
    @Override
    public boolean addSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            if (!subscribers.containsKey(subscriber.getId())) {
                subscribers.put(subscriber.getId(), subscriber);
                syncWithPeers(subscriber);
                return true;
            }
            return false;
        } finally {
            subscriberLock.unlock();
        }
    }
    
    @Override
    public void setConfiguration(Configuration config) {
        this.config = config;
        if (config.getFaultToleranceLevel() > 0) {
            CompletableFuture.runAsync(this::connectToPeers);
        }
    }
    
    private void connectToPeers() {
        // Server1'e bağlan
        try {
            Socket socket = new Socket("localhost", 5001);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(SERVER_ID);
            peerConnections.put(1, socket);
            System.out.println("Server" + SERVER_ID + ": Server1'e bağlandı");
            CompletableFuture.runAsync(() -> listenToPeer(1, socket));
        } catch (IOException e) {
            System.err.println("Server1'e bağlanılamadı: " + e.getMessage());
        }

        // Server2'ye bağlan
        try {
            Socket socket = new Socket("localhost", 5002);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(SERVER_ID);
            peerConnections.put(2, socket);
            System.out.println("Server" + SERVER_ID + ": Server2'ye bağlandı");
            CompletableFuture.runAsync(() -> listenToPeer(2, socket));
        } catch (IOException e) {
            System.err.println("Server2'ye bağlanılamadı: " + e.getMessage());
        }
    }
    
    private void listenToPeer(int peerId, Socket peerSocket) {
        try {
            InputStream input = peerSocket.getInputStream();
            while (isRunning && !peerSocket.isClosed()) {
                Message message = Message.parseDelimitedFrom(input);
                if (message != null && "SYNC".equals(message.getDemand())) {
                    int length = new DataInputStream(input).readInt();
                    byte[] data = new byte[length];
                    input.read(data);
                    Subscriber subscriber = Subscriber.parseFrom(data);
                    updateSubscriber(subscriber);
                    System.out.println("Server3: Peer" + peerId + "'den senkronizasyon alındı");
                }
            }
        } catch (IOException e) {
            System.err.println("Server3: Peer" + peerId + " bağlantısı koptu: " + e.getMessage());
            peerConnections.remove(peerId);
        }
    }
    
    private void updateSubscriber(Subscriber subscriber) {
        subscriberLock.lock();
        try {
            subscribers.put(subscriber.getId(), subscriber);
        } finally {
            subscriberLock.unlock();
        }
    }
    
    private void syncWithPeers(Subscriber subscriber) {
        if (config != null && config.getFaultToleranceLevel() > 0) {
            Message syncMessage = Message.newBuilder()
                .setDemand("SYNC")
                .setResponse(Message.Response.YEP)
                .setTimestamp(System.currentTimeMillis())
                .build();

            for (Map.Entry<Integer, Socket> peer : peerConnections.entrySet()) {
                sendToPeer(peer.getKey(), syncMessage);
            }
        }
    }
    
    private void sendToPeer(int peerId, Message message) {
        Socket peerSocket = peerConnections.get(peerId);
        if (peerSocket != null && !peerSocket.isClosed()) {
            try {
                message.writeDelimitedTo(peerSocket.getOutputStream());
                System.out.println("Server3: Peer" + peerId + "'e senkronizasyon gönderildi");
            } catch (IOException e) {
                System.err.println("Server3: Peer" + peerId + "'e mesaj gönderilemedi: " + e.getMessage());
                peerConnections.remove(peerId);
            }
        }
    }
    
    @Override
    public int getActiveSubscriberCount() {
        return (int) subscribers.values().stream()
            .filter(s -> s.getStatus() == Status.ONLN)
            .count();
    }
    
    private void handlePeerConnection(Socket socket) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            int peerId = in.readInt(); // Bağlanan peer'ın ID'sini al
            peerConnections.put(peerId, socket);
            System.out.println("Server" + SERVER_ID + ": Peer" + peerId + " bağlandı");
            
            // Peer dinleme thread'ini başlat
            CompletableFuture.runAsync(() -> listenToPeer(peerId, socket));
        } catch (IOException e) {
            System.err.println("Peer bağlantısı başlatılamadı: " + e.getMessage());
        }
    }
    
    @Override
    public Subscriber getSubscriber(int id) {
        return subscribers.get(id);
    }
    
    public static void main(String[] args) {
        Server3 server = new Server3();
        
        // Sadece admin portunu dinlemeye başla
        server.start();  // Bu sadece admin portunu açacak
        System.out.println("Server3 admin portu dinleniyor: 7003");
        
        // Ana thread'i beklet
        server.waitForShutdown();
    }
} 