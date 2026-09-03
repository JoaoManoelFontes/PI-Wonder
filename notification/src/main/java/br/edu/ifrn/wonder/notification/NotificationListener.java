package br.edu.ifrn.wonder.notification;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

	@RabbitListener(queues = RabbitConfig.NOTIFICATION_QUEUE)
	public void handle(String message) {
		System.out.println("Notificacao recebida: " + message);
	}

}
