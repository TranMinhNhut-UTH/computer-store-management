package com.computerstore.repository;

import com.computerstore.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByDefaultAddressDescIdDesc(Long userId);
    List<Address> findByUserIdAndDefaultAddressTrue(Long userId);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
    Optional<Address> findFirstByUserIdOrderByIdDesc(Long userId);
    boolean existsByUserId(Long userId);
}
