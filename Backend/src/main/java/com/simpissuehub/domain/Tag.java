package com.simpissuehub.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "tags")
public class Tag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(nullable = false, unique = true, length = 32) public String name;
    @Column(nullable = false, length = 7) public String color;
}
