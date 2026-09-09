package org.hzstark.librarymanagment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    
    @Query("SELECT b FROM Book b WHERE LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%')) OR CAST(b.id AS string) = :query")
    List<Book> searchByNameOrId(@Param("query") String query);

    List<Book> findByBorrowerId(Long borrowerId);
    
    List<Book> findByStatus(String status);
}
