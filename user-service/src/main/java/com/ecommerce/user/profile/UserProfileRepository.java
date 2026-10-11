package com.ecommerce.user.profile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    Page<UserProfile> findByEmailContainingIgnoreCase(String email, Pageable pageable);
}
