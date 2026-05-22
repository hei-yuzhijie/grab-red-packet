package yuzhijie.redpacket.common;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisUtil {
    public static String generateId(){
        return UUID.randomUUID().toString().replace("-", "");
    }

}
