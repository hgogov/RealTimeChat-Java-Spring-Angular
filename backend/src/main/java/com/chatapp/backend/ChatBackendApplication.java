package com.chatapp.backend;

import jakarta.annotation.PostConstruct;
// import org.slf4j.Logger; // Keep if you want standard logging
// import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean; // Import Bean
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration; // Import
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory; // Import
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Arrays;

@SpringBootApplication
@EnableKafka
public class ChatBackendApplication {

    // private static final Logger log = LoggerFactory.getLogger(ChatBackendApplication.class);

    private final Environment environment;

    // --- Keep these @Value injections ---
    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort; // Add port injection

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaServers;
    // --- End @Value injections ---

    public ChatBackendApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(ChatBackendApplication.class, args);
    }

    // --- Keep @PostConstruct logging ---
    @PostConstruct
    public void logConfiguration() {
        System.out.println("--- Application Configuration (@PostConstruct) ---");
        System.out.println("Active Spring profiles: " + Arrays.toString(environment.getActiveProfiles()));
        // Log the injected values to see if they are correct *before* the bean is created
        System.out.println("Effective Redis host (@Value before bean): " + redisHost);
        System.out.println("Effective Redis port (@Value before bean): " + redisPort);
        System.out.println("Effective Kafka Servers (@Value before bean): " + kafkaServers);
        System.out.println("Datasource URL (Environment): " + environment.getProperty("spring.datasource.url"));
        System.out.println("Redis Host (Environment): " + environment.getProperty("spring.redis.host"));
        System.out.println("Kafka Servers (Environment): " + environment.getProperty("spring.kafka.bootstrap-servers"));
        System.out.println("--- End Configuration (@PostConstruct) ---");
    }
    // --- End @PostConstruct ---


    // --- ADD Explicit Bean Definition HERE ---
    @Bean
    public LettuceConnectionFactory redisConnectionFactory(
            @Value("${spring.redis.host}") String host, // Inject directly into method
            @Value("${spring.redis.port}") int port) {

        System.out.println("--- Creating LettuceConnectionFactory Bean (ChatBackendApplication) ---");
        System.out.println("Using Redis Host (Injected into bean method): " + host);
        System.out.println("Using Redis Port (Injected into bean method): " + port);
        System.out.println("---------------------------------------------------------------------");

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(config);
    }
    // --- END Explicit Bean Definition ---

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory) {
        // Explicitly configure RedisTemplate to use our connection factory
        System.out.println("--- Creating RedisTemplate Bean ---");
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // Configure serializers (important for String keys etc.)
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer()); // Or Jackson2JsonRedisSerializer if storing objects
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer()); // Or Jackson2JsonRedisSerializer
        template.afterPropertiesSet(); // Initialize
        System.out.println("--- RedisTemplate Bean Created ---");
        return template;
    }

    @Bean // Also define StringRedisTemplate if needed elsewhere
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
        System.out.println("--- Creating StringRedisTemplate Bean ---");
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        // StringRedisTemplate uses String serializers by default
        System.out.println("--- StringRedisTemplate Bean Created ---");
        return template;
    }
}