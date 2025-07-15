package de.mschanzer.chesstest.chesstest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@SpringBootApplication
@EnableJdbcRepositories
public class ChesstestApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChesstestApplication.class, args);
	}

}
