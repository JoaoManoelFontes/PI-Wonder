package br.edu.ifrn.wonder.worker;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerationListener {

	@RabbitListener(queues = RabbitConfig.REPORT_GENERATION_QUEUE)
	public void handle(String message) {
		System.out.println("Job recebido: " + message);
	}

}
