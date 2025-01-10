package dist_servers;

import com.hasup.proto.CapacityProto.Capacity;
import com.hasup.proto.ConfigurationProto.Configuration;
import com.hasup.proto.SubscriberProto.Subscriber;

/**
 * Sunucular için ortak arayüz.
 * Her sunucu bu arayüzü implement etmek zorundadır.
 */
public interface ServerInterface {
    /**
     * Sunucunun ID'sini döndürür (1, 2 veya 3)
     */
    int getServerId();
    
    /**
     * Sunucunun mevcut kapasite bilgisini döndürür
     * (abone sayısı ve zaman bilgisi)
     */
    Capacity getCapacity();
    
    /**
     * Yeni bir abone ekler
     * @param subscriber Eklenecek abone bilgisi
     * @return Ekleme başarılı ise true, değilse false
     */
    boolean addSubscriber(Subscriber subscriber);
    
    /**
     * Sunucu konfigürasyonunu günceller
     * (hata toleransı ve peer sunucu bilgileri)
     * @param config Yeni konfigürasyon
     */
    void setConfiguration(Configuration config);
    
    /**
     * Sunucudaki toplam abone sayısını döndürür
     */
    default int getSubscriberCount() {
        return 0;  // Her sunucu kendi implementasyonunu yapacak
    }
    
    /**
     * Aktif (ONLN) abonelerin sayısını döndürür
     */
    default int getActiveSubscriberCount() {
        return 0;
    }
    
    /**
     * ID'ye göre abone bilgisini döndürür
     */
    Subscriber getSubscriber(int id);
    
    void startServices();
} 