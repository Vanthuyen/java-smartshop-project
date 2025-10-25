package com.example.smartshop.configs;

import com.example.smartshop.commons.enums.Role;
import com.example.smartshop.entities.CategoryEntity;
import com.example.smartshop.entities.ProductEntity;
import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.repositories.CategoryRepository;
import com.example.smartshop.repositories.ProductRepository;
import com.example.smartshop.repositories.UserRepository;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final Faker faker = new Faker(new Locale("en"));

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() > 0 || categoryRepository.count() > 0 || productRepository.count() > 0) {
            System.out.println("✅ Data already exists, skipping seeding...");
            return;
        }

        System.out.println("🚀 Starting data seeding...");


        seedCategories();
        seedUsers();
        seedProducts();

        System.out.println("✅ Data seeding complete!");
    }

    private void seedUsers() {
        List<UserEntity> users = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            UserEntity user = UserEntity.builder()
                    .name(faker.name().fullName())
                    .email(faker.internet().emailAddress())
                    .password(passwordEncoder.encode("123456"))
                    .role(i < 5 ? Role.ADMIN : Role.CUSTOMER)
                    .build();
            users.add(user);
        }

        userRepository.saveAll(users);
        System.out.println("✅ Seeded 100 users");
    }

    private void seedCategories() {
        List<CategoryEntity> categories = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            CategoryEntity category = CategoryEntity.builder()
                    .name(faker.commerce().department() + " " + i)
                    .description(faker.lorem().sentence(5))
                    .build();
            categories.add(category);
        }

        categoryRepository.saveAll(categories);
        System.out.println("✅ Seeded 100 categories");
    }

    private void seedProducts() {
        List<CategoryEntity> categories = categoryRepository.findAll();
        Random random = new Random();
        List<ProductEntity> products = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            CategoryEntity randomCategory = categories.get(random.nextInt(categories.size()));

            ProductEntity product = ProductEntity.builder()
                    .name(faker.commerce().productName() + " " + i)
                    .description(faker.lorem().sentence(6))
                    .price(BigDecimal.valueOf(faker.number().randomDouble(2, 10, 1000)))
                    .stock(faker.number().numberBetween(0, 200))
                    .category(randomCategory)
                    .build();

            products.add(product);

            // lưu theo batch để tránh out of memory
            if (products.size() % 500 == 0) {
                productRepository.saveAll(products);
                products.clear();
                System.out.println("✅ Seeded " + (i + 1) + " products...");
            }
        }

        // lưu phần còn lại
        if (!products.isEmpty()) {
            productRepository.saveAll(products);
        }

        System.out.println("✅ Finished seeding 10,000 products");
    }
}
