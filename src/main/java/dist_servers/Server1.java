package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.SubscriberProto.Status;
import com.hasup.proto.MessageProto.Message;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Server1 implements ServerInterface {
    private final int SERVER_ID = 1;
    private final int ADMIN_PORT = 7001;
    private final int CLIENT_PORT = 6001;
    private final int PEER_PORT = 5001;
    
    private Configuration config;
    private final ConcurrentHashMap<Integer, Subscriber> subscribers;
    private final ReentrantLock subscriberLock;
    private volatile boolean isRunning;
    private final ConcurrentHashMap<Integer, Socket> peerConnections;
    
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    private final AtomicInteger nextId = new AtomicInteger(1);
    
    public Server1() {
        this.subscribers = new ConcurrentHashMap<>();
        this.subscriberLock = new ReentrantLock();
        this.peerConnections = new ConcurrentHashMap<>();
        this.isRunning = false;
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
                    // Server2 ve Server3'ten gelen bağlantıları bekle
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
            // Yeni abone ise
            if (subscriber.getStatus() == Status.SUBS) {
                // ID kontrolü
                int subscriberId = subscriber.getId();
                if (!subscribers.containsKey(subscriberId)) {
                    subscribers.put(subscriberId, subscriber);
                    
                    // Hata toleransı varsa diğer sunuculara bildir
                    if (config != null && config.getFaultToleranceLevel() > 0) {
                        syncWithPeers(subscriber);
                    }
                    
                    System.out.println("Server" + SERVER_ID + ": Yeni abone eklendi - " + 
                        subscriber.getNameSurname() + " (ID: " + subscriberId + ")");
                    return true;
                }
            }
            // Mevcut abone güncelleme
            else if (subscribers.containsKey(subscriber.getId())) {
                Subscriber existing = subscribers.get(subscriber.getId());
                Subscriber updated = Subscriber.newBuilder(existing)
                    .setStatus(subscriber.getStatus())
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                
                subscribers.put(subscriber.getId(), updated);
                
                if (config != null && config.getFaultToleranceLevel() > 0) {
                    syncWithPeers(updated);
                }
                
                System.out.println("Server" + SERVER_ID + ": Abone güncellendi - ID: " + 
                    subscriber.getId() + ", Status: " + subscriber.getStatus());
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
        // Server2'ye bağlan
        try {
            Socket socket = new Socket("localhost", 5002);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(SERVER_ID); // Kendi ID'mizi gönder
            peerConnections.put(2, socket);
            System.out.println("Server" + SERVER_ID + ": Server2'ye bağlandı");
            CompletableFuture.runAsync(() -> listenToPeer(2, socket));
        } catch (IOException e) {
            System.err.println("Server2'ye bağlanılamadı: " + e.getMessage());
        }

        // Server3'e bağlan
        try {
            Socket socket = new Socket("localhost", 5003);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            out.writeInt(SERVER_ID); // Kendi ID'mizi gönder
            peerConnections.put(3, socket);
            System.out.println("Server" + SERVER_ID + ": Server3'e bağlandı");
            CompletableFuture.runAsync(() -> listenToPeer(3, socket));
        } catch (IOException e) {
            System.err.println("Server3'e bağlanılamadı: " + e.getMessage());
        }
    }
    
    private void listenToPeer(int peerId, Socket peerSocket) {
        try {
            InputStream input = peerSocket.getInputStream();
            while (isRunning && !peerSocket.isClosed()) {
                Message message = Message.parseDelimitedFrom(input);
                if (message != null && "SYNC".equals(message.getDemand())) {
                    if (message.getResponse() == Message.Response.YEP) {
                        Subscriber subscriber = Subscriber.parseDelimitedFrom(input);
                        if (subscriber != null) {
                            updateSubscriber(subscriber);
                            System.out.println("Server" + SERVER_ID + ": Peer" + peerId + "'den YEP yanıtı alındı");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Server" + SERVER_ID + ": Peer" + peerId + " bağlantısı koptu: " + e.getMessage());
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
            // SYNC mesajını oluştur
            Message syncMessage = Message.newBuilder()
                .setDemand("SYNC")
                .setResponse(Message.Response.YEP)  // YEP kullan
                .setTimestamp(System.currentTimeMillis())
                .build();

            // Her peer'a gönder
            for (Map.Entry<Integer, Socket> peer : peerConnections.entrySet()) {
                sendToPeer(peer.getKey(), syncMessage, subscriber);
            }
        }
    }
    
    private void sendToPeer(int peerId, Message message, Subscriber subscriber) {
        Socket peerSocket = peerConnections.get(peerId);
        if (peerSocket != null && !peerSocket.isClosed()) {
            try {
                // Önce mesajı gönder
                message.writeDelimitedTo(peerSocket.getOutputStream());
                
                // Sonra subscriber verisini gönder
                subscriber.writeDelimitedTo(peerSocket.getOutputStream());
                
                System.out.println("Server" + SERVER_ID + ": Peer" + peerId + "'e YEP yanıtı gönderildi");
            } catch (IOException e) {
                System.err.println("Server" + SERVER_ID + ": Peer" + peerId + "'e mesaj gönderilemedi: " + e.getMessage());
                peerConnections.remove(peerId);
            }
        }
    }
    
    @Override
    public int getSubscriberCount() {
        return subscribers.size();
    }
    
    private int generateNextId() {
        return nextId.getAndIncrement();
    }
    
    @Override
    public int getActiveSubscriberCount() {
        return (int) subscribers.values().stream()
            .filter(s -> s.getStatus() == Status.ONLN)
            .count();
    }
    
    @Override
    public Subscriber getSubscriber(int id) {
        return subscribers.get(id);
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
    
    public static void main(String[] args) {
        Server1 server = new Server1();
        
        // Sadece admin portunu dinlemeye başla
        server.start();  // Bu sadece admin portunu açacak
        System.out.println("Server1 admin portu dinleniyor: 7001");
        
        // Ana thread'i beklet
        server.waitForShutdown();
    }
} 