package org.hzstark.librarymanagment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    List<Admin> findByEmail(String email);
}
