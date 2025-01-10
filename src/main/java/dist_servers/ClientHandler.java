package dist_servers;

import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.MessageProto.Message;
import java.io.*;
import java.net.Socket;
import com.hasup.proto.SubscriberProto.Status;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerInterface server;

    public ClientHandler(Socket socket, ServerInterface server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // İstek boyutunu oku
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);

            // Subscriber nesnesini parse et
            Subscriber request = Subscriber.parseFrom(data);
            System.out.println("İstek alındı: " + request.getNameSurname());

            Subscriber response = null;
            if (request.getStatus() == Status.SUBS) {
                // Yeni ID ata ve abone oluştur
                int newId = ((Server1)server).generateNextId();
                Subscriber newSubscriber = Subscriber.newBuilder(request)
                    .setId(newId)
                    .setStartDate(System.currentTimeMillis())
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                
                // Server'a ekle
                if (server.addSubscriber(newSubscriber)) {
                    response = newSubscriber;
                    System.out.println("Abone eklendi, ID: " + newId);
                }
            }

            // Yanıt gönder
            if (response != null) {
                byte[] responseData = response.toByteArray();
                out.writeInt(responseData.length);
                out.write(responseData);
                out.flush();
                System.out.println("Yanıt gönderildi: ID=" + response.getId());
            } else {
                // Başarısız durumda error yanıtı gönder
                Subscriber errorResponse = Subscriber.newBuilder(request)
                    .setStatus(Status.UNKNOWN)
                    .build();
                byte[] responseData = errorResponse.toByteArray();
                out.writeInt(responseData.length);
                out.write(responseData);
                out.flush();
                System.out.println("İşlem başarısız - UNKNOWN yanıtı gönderildi");
            }

        } catch (IOException e) {
            System.err.println("Client handler hatası: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Socket kapatma hatası: " + e.getMessage());
            }
        }
    }
} 