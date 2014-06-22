import socket
import sys
import logging


class FanovaRemote(object):
    IP = "127.0.0.1"
    TCP_PORT = 5050
    #The size of a udp package
    #note: set in SMAC using --ipc-udp-packetsize

    def __init__(self):
        self.connected = False
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        self._conn = None

        #try to find a free port:
        for self.port in range(FanovaRemote.TCP_PORT, FanovaRemote.TCP_PORT + 1000):
            try:
                self._sock.bind((FanovaRemote.IP, self.port))
                self._sock.listen(1)
                break
            except:
                pass
        logging.debug("Communicating on port: %d", self.port)

#TODO: Write function that adds ':' automatically

    def __del__(self):
        if self._conn is not None:
            self._conn.close()
        self._sock.close()

    def connect(self, timeout=None):
        self._sock.settimeout(timeout)
        self._conn, addr = self._sock.accept()
        self._conn.settimeout(None)
        self.connected = True

    def disconnect(self):
        self._conn.close()
        self._conn = None
        self.connected = False

    def send(self, data):
        assert self._conn is not None
        logging.debug("> " + str(data))
        self._conn.sendall(data + "\n")

    def receive(self):
        assert self._conn is not None
        #data = self._conn.recv(4096) # buffer size is 4096 bytes
        fconn = self._conn.makefile('r')
        data = fconn.readline()

        logging.debug("< " + str(data))
        return data
