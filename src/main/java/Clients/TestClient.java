package Clients;

import com.hasup.proto.SubscriberProto.Subscriber;
import com.hasup.proto.SubscriberProto.Status;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Date;
import java.io.IOException;

public class TestClient {
    public static void main(String[] args) {
        System.out.println("Test başlatılıyor...");
        
        // Test için 5 abone oluştur
        for (int i = 1; i <= 5; i++) {
            Client client = new Client("test-client-" + i);
            
            // Her aboneyi farklı sunuculara dağıt (1, 2, 3, 1, 2 şeklinde)
            int serverId = ((i - 1) % 3) + 1;
            
            try {
                if (client.connect(serverId)) {
                    System.out.println("Server" + serverId + "'e bağlanıldı");
                    
                    // Yeni abone oluştur
                    Subscriber subscriber = Subscriber.newBuilder()
                        .setId(0)  // ID'yi 0 olarak ayarla, sunucu yeni ID atayacak
                        .setStatus(Status.SUBS)
                        .setNameSurname("Test User " + i)
                        .setStartDate(System.currentTimeMillis())
                        .setLastAccessed(System.currentTimeMillis())
                        .addInterests("sports")
                        .addInterests("technology")
                        .build();
                    
                    // Yanıtı bekle ve kontrol et
                    try {
                        Subscriber response = client.subscribe(subscriber);
                        if (response != null && response.getId() > 0) {
                            System.out.println("Abone kaydı başarılı: " + response.getNameSurname() + 
                                " (Server" + serverId + ", ID: " + response.getId() + ")");
                        } else {
                            System.out.println("Abone kaydı başarısız, diğer sunucular deneniyor...");
                            // Diğer sunucuları dene
                            for (int retry = 1; retry <= 3; retry++) {
                                if (retry != serverId) {
                                    System.out.println("Server" + retry + " deneniyor...");
                                    if (client.connect(retry)) {
                                        response = client.subscribe(subscriber);
                                        if (response != null && response.getId() > 0) {
                                            System.out.println("Abone kaydı başarılı: " + response.getNameSurname() + 
                                                " (Server" + retry + ", ID: " + response.getId() + ")");
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Bağlantı hatası: " + e.getMessage());
                    }
                } else {
                    System.out.println("Server" + serverId + "'e bağlantı başarısız");
                }
            } catch (Exception e) {
                System.err.println("Hata: " + e.getMessage());
            } finally {
                client.close();
                // Sunucular arası senkronizasyon için biraz bekle
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        System.out.println("Test tamamlandı.");
    }
} 