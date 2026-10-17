package com.simpissuehub.domain;
import jakarta.persistence.*;
@Entity @Table(name="issue_categories")
public class IssueCategory { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(nullable=false,unique=true,length=32) public String name; }
