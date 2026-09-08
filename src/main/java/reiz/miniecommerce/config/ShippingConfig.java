package reiz.miniecommerce.config;

import reiz.miniecommerce.modules.shipments.adapters.out.cep.ShippingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfig {
}
