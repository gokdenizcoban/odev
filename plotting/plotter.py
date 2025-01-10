import os
import sys

# Proto dosyasının bulunduğu dizini Python path'ine ekle
current_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(current_dir)

import socket
import struct
import threading
import matplotlib.pyplot as plt
from matplotlib.animation import FuncAnimation
from Capacity_pb2 import Capacity

class CapacityPlotter:
    def __init__(self):
        # Her sunucu için veri yapısı
        self.server_data = {
            1: {'times': [], 'values': [], 'color': 'red'},
            2: {'times': [], 'values': [], 'color': 'blue'},
            3: {'times': [], 'values': [], 'color': 'green'}
        }
        
        # Plot ayarları
        plt.style.use('dark_background')
        self.fig, self.ax = plt.subplots(figsize=(12, 6))
        self.ax.set_title('Sunucu Doluluk Oranları (Canlı)', pad=20)
        self.ax.set_xlabel('Zaman (sn)')
        self.ax.set_ylabel('Doluluk')
        
        # Lejant için renkli çizgiler
        for server_id, data in self.server_data.items():
            self.ax.plot([], [], color=data['color'], 
                        label=f'Server {server_id}', linewidth=2)
        self.ax.legend()
        
        # Socket server
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.bind(('localhost', 9000))
        self.server_socket.listen(1)
        
    def start(self):
        print("Plotter başlatıldı, admin.rb bekleniyor...")
        client, addr = self.server_socket.accept()
        print(f"Admin bağlandı: {addr}")
        
        threading.Thread(target=self.handle_admin, args=(client,)).start()
        self.ani = FuncAnimation(self.fig, self.update_plot, interval=1000)
        plt.show()
            
    def handle_admin(self, client):
        try:
            while True:
                length_data = client.recv(4)
                if not length_data:
                    break
                    
                length = struct.unpack('!I', length_data)[0]
                data = client.recv(length)
                capacity = Capacity()
                capacity.ParseFromString(data)
                
                server_id = capacity.server_id
                if server_id in self.server_data:
                    self.server_data[server_id]['times'].append(capacity.timestamp)
                    self.server_data[server_id]['values'].append(capacity.server_status)
                    
                    if len(self.server_data[server_id]['times']) > 50:
                        self.server_data[server_id]['times'].pop(0)
                        self.server_data[server_id]['values'].pop(0)
                        
        except Exception as e:
            print(f"Admin bağlantısı koptu: {e}")
        finally:
            client.close()
            
    def update_plot(self, frame):
        self.ax.clear()
        
        for server_id, data in self.server_data.items():
            if data['times']:
                relative_times = [(t - data['times'][0])/1000 for t in data['times']]
                self.ax.plot(relative_times, data['values'], 
                           color=data['color'], 
                           label=f'Server {server_id}',
                           linewidth=2)
                
        self.ax.set_title('Sunucu Doluluk Oranları (Canlı)', pad=20)
        self.ax.set_xlabel('Zaman (sn)')
        self.ax.set_ylabel('Doluluk')
        self.ax.legend()
        self.ax.grid(True, alpha=0.3)

if __name__ == "__main__":
    plotter = CapacityPlotter()
    plotter.start()
