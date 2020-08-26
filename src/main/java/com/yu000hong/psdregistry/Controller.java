package com.yu000hong.psdregistry;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class Controller {

    @Resource
    private Registry registry;

    @PostMapping("/register")
    public Map register(
        @RequestParam("service") String service,
        @RequestParam("host") String host,
        @RequestParam("port") int port) {
        registry.register(service, host, port);
        return ImmutableMap.of("code", 0, "msg", "success");
    }

    @PostMapping("/unregister")
    public Map unregister(
        @RequestParam("service") String service,
        @RequestParam("host") String host,
        @RequestParam("port") int port) {
        registry.unregister(service, host, port);
        return ImmutableMap.of("code", 0, "msg", "success");
    }

}
