package com.yu000hong.psdregistry;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.twitter.finagle.common.zookeeper.ServerSet;
import com.twitter.finagle.common.zookeeper.ServerSet.EndpointStatus;
import com.twitter.finagle.common.zookeeper.ServerSet.UpdateException;
import com.twitter.finagle.common.zookeeper.ServerSetImpl;
import com.twitter.finagle.common.zookeeper.ZooKeeperClient;
import com.twitter.util.Duration;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class Registry {

    @Resource
    private RestTemplate restTemplate;
    @Value("${zookeeper.prefix}")
    private String zkPrefix;
    @Value("${zookeeper.port}")
    private int zkPort;
    @Value("${zookeeper.servers}")
    private List<String> zkServers;

    private final ConcurrentHashMap<String, Server> servers;

    public Registry() {
        this.servers = new ConcurrentHashMap<>(64);
    }

    public void register(String service, String host, int port) {
        String key = genKey(service, host, port);
        if (servers.containsKey(key)) {
            return;
        }
        Server server = new Server(service, host, port);
        server.register();
        if (servers.putIfAbsent(key, server) != null) {
            server.unregister();
        }
    }

    public void unregister(String service, String host, int port) {
        String key = genKey(service, host, port);
        Server server = servers.get(key);
        if (server != null) {
            servers.remove(key);
            server.unregister();
        }
    }


    @Scheduled(fixedRate = 10000)
    public void check() {
        Map<String, Server> cloneServers = new HashMap<>(servers);
        for (String key : cloneServers.keySet()) {
            Server server = cloneServers.get(key);
            if (!isAlive(server)) {
                servers.remove(key);
                server.unregister();
            }
        }
    }

    @PreDestroy
    public void destroy() {
        Map<String, Server> cloneServers = new HashMap<>(servers);
        for (String key : cloneServers.keySet()) {
            Server server = cloneServers.get(key);
            servers.remove(key);
            server.unregister();
        }
    }

    private String genKey(String service, String host, int port) {
        return service + ":" + host + ":" + port;
    }

    private boolean isAlive(Server server) {
        String url = server.genAliveUrl();
        try {
            ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.error(e.getMessage(), e);
            return false;
        }
    }

    private class Server {

        private final String service;
        private final String host;
        private final int port;
        private ZooKeeperClient client;
        private EndpointStatus status;

        private Server(String service, String host, int port) {
            this.service = service;
            this.host = host;
            this.port = port;
        }

        private void register() {
            if (status != null) {
                return;
            }
            List<InetSocketAddress> addresses = zkServers.stream()
                .map(host -> new InetSocketAddress(host, zkPort))
                .collect(Collectors.toList());
            try {
                client = new ZooKeeperClient(Duration.apply(1000, MILLISECONDS), addresses);
                ServerSet serverSet = new ServerSetImpl(client, zkPrefix + service);
                status = serverSet.join(new InetSocketAddress(host, port), new HashMap<>(0));
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                unregister();
                throw new RuntimeException(e);
            }
        }

        private void unregister() {
            if (status != null) {
                try {
                    status.leave();
                } catch (UpdateException e) {
                    log.error(e.getMessage(), e);
                }
                status = null;
            }
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }
                client = null;
            }
        }

        private String genAliveUrl() {
            return "http://" + host + ":" + port + "/alive";
        }
    }

}
