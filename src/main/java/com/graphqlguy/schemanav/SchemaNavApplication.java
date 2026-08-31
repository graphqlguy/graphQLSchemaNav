package com.graphqlguy.schemanav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The starting point for Class 3 of the "GraphQL for AI Agents" course.
 *
 * This branch carries the project skeleton, the Maven dependencies, the Movie
 * Database schema, and a small labelled benchmark file, and nothing else. Class 3
 * builds the instrument on top: the corpus renderer, the two retrieval backends,
 * the benchmark harness, and the agent loop that reads the results.
 */
@SpringBootApplication
public class SchemaNavApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchemaNavApplication.class, args);
    }
}
