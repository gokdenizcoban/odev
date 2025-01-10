package dist_servers;

import Clients.Client;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Status;
import com.hasup.proto.SubscriberProto.Subscriber;
import java.util.*;
import java.util.concurrent.*;

public class FaultToleranceTest {
    private Server1 server1;
    private Server2 server2;
    private Server3 server3;
    private List<Client> clients;
    
    public FaultToleranceTest() {
        server1 = new Server1();
        server2 = new Server2();
        server3 = new Server3();
        clients = new ArrayList<>();
    }
    
    public void startServers() {
        System.out.println("Sunucular başlatılıyor...");
        
        // Her sunucu için ayrı thread başlat
        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> server1.start()),
            CompletableFuture.runAsync(() -> server2.start()),
            CompletableFuture.runAsync(() -> server3.start())
        );
        
        // Sunucuların başlamasını bekle
        sleep(2000);
        System.out.println("Tüm sunucular başlatıldı.");
    }
    
    public void configureServers(int faultTolerance) {
        System.out.println("Sunucular yapılandırılıyor. Hata toleransı: " + faultTolerance);
        
        // Her sunucu için konfigürasyon oluştur ve gönder
        Configuration config1 = createConfig(1, faultTolerance);
        Configuration config2 = createConfig(2, faultTolerance);
        Configuration config3 = createConfig(3, faultTolerance);
        
        server1.setConfiguration(config1);
        server2.setConfiguration(config2);
        server3.setConfiguration(config3);
        
        sleep(1000);
        System.out.println("Sunucu konfigürasyonları tamamlandı.");
    }
    
    private Configuration createConfig(int serverId, int faultTolerance) {
        return Configuration.newBuilder()
            .setServerId(serverId)
            .setFaultToleranceLevel(faultTolerance)
            .setMethod("STRT")
            .build();
    }
    
    private List<String> getPeerServers(int serverId) {
        List<String> peers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            if (i != serverId) {
                peers.add(String.valueOf(i));
            }
        }
        return peers;
    }
    
    public void createSubscribers(int count) {
        System.out.println(count + " abone oluşturuluyor...");
        
        for (int i = 0; i < count; i++) {
            Client client = new Client("test-client-" + i);
            clients.add(client);
            
            int serverId = 1 + new Random().nextInt(3);
            if (client.connect(serverId)) {
                Subscriber subscriber = Subscriber.newBuilder()
                    .setStatus(Status.SUBS)
                    .setNameSurname("User" + i)
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                    
                if (client.subscribe(subscriber)) {
                    System.out.println("Abone oluşturuldu: User" + i + " (Server" + serverId + ")");
                }
            }
        }
        
        sleep(1000);
        System.out.println("Tüm aboneler oluşturuldu.");
    }
    
    public void testFaultTolerance() {
        System.out.println("\nHata toleransı testi başlatılıyor...");
        
        // Server1'i düzgün şekilde kapat
        System.out.println("Server1 kapatılıyor...");
        if (server1 != null) {
            server1.stop();
            server1 = null;
        }
        System.gc();
        sleep(2000);  // Kapanma için biraz daha bekle
        
        // Yeni client ile bağlantı testi
        Client newClient = new Client("fault-test-client");
        System.out.println("Yeni client bağlantı testi yapılıyor...");
        
        if (!newClient.connect(1)) {
            System.out.println("Server1'e bağlantı başarısız (beklenen durum)");
            if (newClient.connect(2) || newClient.connect(3)) {
                System.out.println("Yedek sunucuya bağlantı başarılı!");
                Subscriber subscriber = Subscriber.newBuilder()
                    .setStatus(Status.SUBS)
                    .setNameSurname("faultuser")
                    .setLastAccessed(System.currentTimeMillis())
                    .build();
                if (newClient.subscribe(subscriber)) {
                    System.out.println("Yeni abone kaydı başarılı.");
                }
            }
        }
        
        // Mevcut abonelerin durumunu kontrol et
        checkExistingSubscribers();
    }
    
    private void checkExistingSubscribers() {
        System.out.println("\nMevcut abonelerin durumu kontrol ediliyor...");
        for (Client client : clients) {
            if (client.isConnected()) {
                System.out.println("Abone " + client.getClientId() + " hala bağlı.");
            } else {
                System.out.println("Abone " + client.getClientId() + " bağlantısı kopmuş, yeniden bağlanılıyor...");
                if (client.connect(2) || client.connect(3)) {
                    System.out.println("Abone " + client.getClientId() + " yedek sunucuya bağlandı.");
                }
            }
        }
    }
    
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sleep interrupted: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        FaultToleranceTest test = new FaultToleranceTest();
        
        try {
            test.startServers();
            test.configureServers(2);  // Maksimum hata toleransı
            test.createSubscribers(5); // 5 test abonesi oluştur
            test.testFaultTolerance(); // Hata toleransını test et
            
            System.out.println("\nTest başarıyla tamamlandı!");
        } catch (Exception e) {
            System.err.println("Test sırasında hata oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }
}