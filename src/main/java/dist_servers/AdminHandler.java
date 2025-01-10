package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.MessageProto.Message;
import java.io.*;
import java.net.Socket;

public class AdminHandler implements Runnable {
    private final Socket socket;
    private final ServerInterface server;

    public AdminHandler(Socket socket, ServerInterface server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            while (!socket.isClosed()) {
                try {
                    int messageType = in.read();
                    System.out.println("Gelen mesaj tipi: " + messageType);

                    // Mesaj uzunluğunu oku
                    int length = in.readInt();
                    byte[] messageBytes = new byte[length];
                    in.readFully(messageBytes);

                    if (messageType == 1) {  // Capacity request
                        handleCapacityRequest(messageBytes, out);
                    } else if (messageType == 2) {  // Configuration/STRT
                        handleConfigurationRequest(messageBytes, out);
                    } else if (messageType == 3) {  // CPCTY query
                        handleCapacityQuery(messageBytes, out);
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void handleCapacityRequest(byte[] data, DataOutputStream out) throws IOException {
        try {
            Capacity request = Capacity.parseFrom(data);
            System.out.println("Kapasite isteği alındı: Server" + request.getServerId());

            if (request.getServerId() == server.getServerId()) {
                int activeCount = server.getActiveSubscriberCount();
                
                Capacity response = Capacity.newBuilder()
                    .setServerId(server.getServerId())
                    .setServerStatus(activeCount)
                    .setTimestamp(System.currentTimeMillis())
                    .build();
                
                byte[] responseBytes = response.toByteArray();
                
                // Önce yanıt boyutunu gönder
                out.writeInt(responseBytes.length);
                
                // Sonra yanıtı gönder
                out.write(responseBytes);
                out.flush();
                
                System.out.println("Kapasite yanıtı gönderildi: " + activeCount + " aktif abone");
            } else {
                System.out.println("Server ID uyuşmazlığı: Beklenen=" + server.getServerId() + 
                                 ", Gelen=" + request.getServerId());
            }
        } catch (Exception e) {
            System.err.println("Kapasite isteği işlenirken hata: " + e.getMessage());
            e.printStackTrace();
            
            // Hata durumunda boş yanıt gönder
            out.writeInt(0);
            out.flush();
        }
    }
    
    private void handleConfigurationRequest(byte[] data, DataOutputStream out) throws IOException {
        try {
            Configuration config = Configuration.parseFrom(data);
            System.out.println("Konfigürasyon alındı: Server" + config.getServerId());

            Message response;
            if (config.getServerId() == server.getServerId()) {
                if ("STRT".equals(config.getMethod())) {
                    server.setConfiguration(config);
                    server.startServices(); // Servisleri başlat
                    
                    response = Message.newBuilder()
                        .setDemand("STRT")
                        .setResponse(Message.Response.YEP)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                    
                    System.out.println("Server" + server.getServerId() + 
                        ": Başlama komutu alındı (Tolerans=" + config.getFaultToleranceLevel() + ")");
                } else {
                    response = Message.newBuilder()
                        .setDemand("STRT")
                        .setResponse(Message.Response.NOP)
                        .build();
                }
            } else {
                response = Message.newBuilder()
                    .setDemand("STRT")
                    .setResponse(Message.Response.NOP)
                    .build();
            }

            byte[] responseBytes = response.toByteArray();
            out.writeInt(responseBytes.length);
            out.write(responseBytes);
            out.flush();
            
            System.out.println("Yanıt gönderildi: " + response.getResponse());
        } catch (Exception e) {
            System.err.println("Konfigürasyon işlenirken hata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleCapacityQuery(byte[] data, DataOutputStream out) throws IOException {
        try {
            Message request = Message.parseFrom(data);
            if ("CPCTY".equals(request.getDemand())) {
                // Kapasite bilgisini hazırla
                Capacity response = Capacity.newBuilder()
                    .setServerId(server.getServerId())
                    .setServerStatus(server.getSubscriberCount())
                    .setTimestamp(System.currentTimeMillis())
                    .build();

                // Yanıtı gönder
                byte[] responseBytes = response.toByteArray();
                out.writeInt(responseBytes.length);
                out.write(responseBytes);
                out.flush();

                System.out.println("Kapasite bilgisi gönderildi: " + response.getServerStatus());
            }
        } catch (Exception e) {
            System.err.println("Kapasite sorgusu işlenirken hata: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 