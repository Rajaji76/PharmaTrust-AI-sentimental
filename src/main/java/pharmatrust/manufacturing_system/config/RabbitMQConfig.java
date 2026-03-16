package pharmatrust.manufacturing_system.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for async batch processing
 * Defines queues, exchanges, dead-letter queues, and retry mechanism
 */
@Configuration
public class RabbitMQConfig {

    @Value("${queue.unit-generation:unit-generation-queue}")
    private String unitGenerationQueue;

    @Value("${queue.qr-generation:qr-generation-queue}")
    private String qrGenerationQueue;

    @Value("${queue.blockchain-mint:blockchain-mint-queue}")
    private String blockchainMintQueue;

    private static final String DEAD_LETTER_EXCHANGE = "pharmatrust.dlx";
    private static final String DEAD_LETTER_QUEUE = "pharmatrust.dead-letter-queue";
    private static final String MAIN_EXCHANGE = "pharmatrust.exchange";

    // ============================================
    // Main Exchange
    // ============================================
    @Bean
    public DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE, true, false);
    }

    // ============================================
    // Dead Letter Exchange & Queue
    // ============================================
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DEAD_LETTER_QUEUE);
    }

    // ============================================
    // Unit Generation Queue
    // ============================================
    @Bean
    public Queue unitGenerationQueue() {
        return QueueBuilder.durable(unitGenerationQueue)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Binding unitGenerationBinding() {
        return BindingBuilder.bind(unitGenerationQueue())
                .to(mainExchange())
                .with(unitGenerationQueue);
    }

    // ============================================
    // QR Generation Queue
    // ============================================
    @Bean
    public Queue qrGenerationQueue() {
        return QueueBuilder.durable(qrGenerationQueue)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding qrGenerationBinding() {
        return BindingBuilder.bind(qrGenerationQueue())
                .to(mainExchange())
                .with(qrGenerationQueue);
    }

    // ============================================
    // Blockchain Mint Queue
    // ============================================
    @Bean
    public Queue blockchainMintQueue() {
        return QueueBuilder.durable(blockchainMintQueue)
                .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DEAD_LETTER_QUEUE)
                .build();
    }

    @Bean
    public Binding blockchainMintBinding() {
        return BindingBuilder.bind(blockchainMintQueue())
                .to(mainExchange())
                .with(blockchainMintQueue);
    }

    // ============================================
    // Message Converter & Template
    // ============================================
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        template.setExchange(MAIN_EXCHANGE);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setDefaultRequeueRejected(false); // Send to DLQ on failure
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(10);
        factory.setAutoStartup(false); // Don't start listeners if RabbitMQ is unavailable
        return factory;
    }
}
