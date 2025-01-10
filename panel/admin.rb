# encoding: utf-8
require 'socket'
require_relative 'lib/hasup/capacity_pb'
require_relative 'lib/hasup/configuration_pb'
require_relative 'lib/hasup/message_pb'

class AdminClient
  SERVERS = {
    1 => { host: 'localhost', port: 7001 },
    2 => { host: 'localhost', port: 7002 },
    3 => { host: 'localhost', port: 7003 }
  }

  def initialize
    @sockets = {}
    read_config
  end

  def read_config
    begin
      @fault_tolerance = File.read('dist_subs.conf').match(/fault_tolerance_level\s*=\s*(\d+)/)[1].to_i
      puts "Hata tolerans seviyesi: #{@fault_tolerance}".encode('utf-8')
    rescue => e
      puts "Konfigürasyon dosyası okunamadı: #{e.message}".encode('utf-8')
      exit 1
    end
  end

  def send_start_command(server_id)
    socket = @sockets[server_id]
    return false unless socket

    begin
      # Configuration mesajını hazırla
      config = Hasup::Configuration.new(
        fault_tolerance_level: @fault_tolerance,
        method: "STRT",
        server_id: server_id
      )

      # Mesaj tipini gönder (2 = Configuration)
      socket.write([2].pack('C'))
      socket.flush

      # Protobuf mesajını gönder
      data = config.to_proto
      socket.write([data.bytesize].pack('N'))
      socket.write(data)
      socket.flush

      puts "Server #{server_id}'e başlama komutu gönderildi".encode('utf-8')

      # Yanıtı bekle
      response_size = socket.read(4).unpack('N')[0]
      response_data = socket.read(response_size)
      response = Hasup::Message.decode(response_data)

      success = response.response == :YEP
      puts "Server #{server_id} yanıtı: #{response.response}".encode('utf-8')
      
      success
    rescue => e
      puts "Başlama komutu gönderilemedi: #{e.message}".encode('utf-8')
      false
    end
  end

  # Sunucuya bağlan
  def connect_to_server(server_id)
    server = SERVERS[server_id]
    return nil unless server

    begin
      socket = TCPSocket.new(server[:host], server[:port])
      @sockets[server_id] = socket
      puts "Server #{server_id} baglandi".encode('utf-8')
      true
    rescue Errno::ECONNREFUSED => e
      puts "Server #{server_id} baglanamadi: Sunucu calismiyordu".encode('utf-8')
      false
    rescue => e
      puts "Server #{server_id} baglanamadi: #{e.message}".encode('utf-8')
      false
    end
  end

  # Sunucunun kapasitesini sorgula
  def query_capacity(server_id)
    socket = @sockets[server_id]
    return nil unless socket

    begin
      puts "Kapasite sorgusu gonderiliyor...".encode('utf-8')
      
      request = Hasup::Capacity.new(
        server_id: server_id,
        server_status: 0,
        timestamp: Time.now.to_i
      )

      # 1. Mesaj tipini gönder (1 byte)
      socket.write([1].pack('C'))
      socket.flush
      puts "Mesaj tipi gonderildi (1)".encode('utf-8')

      # 2. Protobuf mesajını gönder
      data = request.to_proto
      socket.write([data.bytesize].pack('N'))  # 4 byte uzunluk
      socket.write(data)                       # mesaj içeriği
      socket.flush
      puts "Protobuf mesaji gonderildi (#{data.bytesize} byte)".encode('utf-8')

      puts "Sunucudan yanit bekleniyor...".encode('utf-8')

      # 3. Yanıt uzunluğunu oku
      response_size_data = socket.read(4)
      if response_size_data.nil? || response_size_data.empty?
        raise "Sunucudan yanit alinamadi"
      end
      
      response_size = response_size_data.unpack('N')[0]
      puts "Yanit boyutu: #{response_size} byte".encode('utf-8')

      # 4. Yanıt verisini oku
      response_data = socket.read(response_size)
      if response_data.nil? || response_data.empty?
        raise "Yanit verisi okunamadi"
      end

      response = Hasup::Capacity.decode(response_data)
      puts "Sunucu #{server_id} kapasitesi:".encode('utf-8')
      puts "  Abone sayisi: #{response.server_status}".encode('utf-8')
      puts "  Zaman: #{Time.at(response.timestamp)}".encode('utf-8')
      
      response
    rescue => e
      puts "Kapasite sorgusu basarisiz: #{e.message}".encode('utf-8')
      puts "Hata detayi: #{e.backtrace.join("\n")}".encode('utf-8')
      nil
    end
  end

  # Bağlantıları kapat
  def close
    @sockets.each_value(&:close)
    @sockets.clear
  end

  def monitor_servers
    # Başarılı başlatılan sunucuları takip et
    active_servers = {}
    
    # Önce tüm sunuculara bağlan ve başlat
    (1..3).each do |server_id|
      puts "\nServer #{server_id} bağlantı denemesi:".encode('utf-8')
      if connect_to_server(server_id)
        if send_start_command(server_id)
          active_servers[server_id] = true
          puts "Server #{server_id} başarıyla başlatıldı".encode('utf-8')
        else
          puts "Server #{server_id} başlatılamadı".encode('utf-8')
        end
      end
      sleep(1)
    end

    puts "\nAktif sunucular izlenmeye başlanıyor...".encode('utf-8')
    
    # Aktif sunucuları periyodik olarak sorgula
    while !active_servers.empty?
      active_servers.keys.each do |server_id|
        begin
          query_server_capacity(server_id)
        rescue => e
          puts "Server #{server_id} kapasitesi sorgulanamadı: #{e.message}".encode('utf-8')
          active_servers.delete(server_id)
        end
      end
      sleep(5)  # 5 saniye bekle
    end
  end

  def query_server_capacity(server_id)
    socket = @sockets[server_id]
    return unless socket

    begin
      # CPCTY sorgusu için Message nesnesini hazırla
      message = Hasup::Message.new(
        demand: "CPCTY",
        response: :UNKNOWN,  # İstek için null/unknown
        timestamp: Time.now.to_i
      )

      # Mesaj tipini gönder (3 = Capacity Query)
      socket.write([3].pack('C'))
      socket.flush

      # Message nesnesini gönder
      data = message.to_proto
      socket.write([data.bytesize].pack('N'))
      socket.write(data)
      socket.flush

      puts "Server #{server_id}'e kapasite sorgusu gönderildi".encode('utf-8')

      # Yanıt boyutunu oku
      response_size = socket.read(4).unpack('N')[0]
      response_data = socket.read(response_size)
      
      # Capacity yanıtını parse et
      capacity = Hasup::Capacity.decode(response_data)
      
      # Plotter'a gönder
      send_to_plotter(capacity)
      
      # Ekrana yazdır
      puts "Server #{server_id} kapasitesi:".encode('utf-8')
      puts "  Doluluk: #{capacity.server_status}".encode('utf-8')
      puts "  Zaman: #{Time.at(capacity.timestamp)}".encode('utf-8')
      
      true
    rescue => e
      puts "Server #{server_id} sorgulanamadı: #{e.message}".encode('utf-8')
      false
    end
  end

  def connect_to_plotter
    begin
      @plotter_socket = TCPSocket.new('localhost', 9000)
      puts "Plotter'a bağlandı".encode('utf-8')
    rescue => e
      puts "Plotter'a bağlanılamadı: #{e.message}".encode('utf-8')
      @plotter_socket = nil
    end
  end

  def send_to_plotter(capacity)
    return unless @plotter_socket
    
    begin
      data = capacity.to_proto
      @plotter_socket.write([data.bytesize].pack('N'))
      @plotter_socket.write(data)
      @plotter_socket.flush
    rescue => e
      puts "Plotter'a veri gönderilemedi: #{e.message}".encode('utf-8')
      @plotter_socket = nil
    end
  end
end

# Test
if __FILE__ == $0
  admin = AdminClient.new
  puts "Admin istemcisi başlatıldı".encode('utf-8')

  begin
    admin.monitor_servers
  rescue Interrupt
    puts "\nİzleme sonlandırılıyor...".encode('utf-8')
  ensure
    admin.close
    puts "İşlem tamamlandı.".encode('utf-8')
  end
end
