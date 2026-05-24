package yuzhijie.redpacket.config;



import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 替代 GenericJackson2JsonRedisSerializer
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // 3. 设置 key/value 序列化
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(RedisSerializer.string());

        redisTemplate.afterPropertiesSet();
        return redisTemplate;

    }

}
