package com.coroutines.demo

import kotlinx.coroutines.delay
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class Controller {
	val prices = mapOf("GOOG" to 12.5, "AMZN" to 15.0, "MSFT" to 7.0)

	@GetMapping("/price")
	fun prices(@RequestParam(value = "ticker") ticker: String): Double? {
		Thread.sleep(1000)
		return prices[ticker]
	}

	@GetMapping("/priceSuspending") //only for testing
	suspend fun pricesSuspending(@RequestParam(value = "ticker") ticker: String): Double? {
		delay(1000)
		return prices[ticker]
	}
}

@SpringBootApplication
@Configuration
class Server {
	companion object {
		@JvmStatic fun main(args: Array<String>) {
			SpringApplication.run(Server::class.java, *args)
		}
	}
}