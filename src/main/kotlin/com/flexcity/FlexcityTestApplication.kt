package com.flexcity

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@SpringBootApplication
class Application {

    @Bean
    fun commandLineRunner(ctx: ApplicationContext) = CommandLineRunner {

    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}