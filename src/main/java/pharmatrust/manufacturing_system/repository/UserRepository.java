package pharmatrust.manufacturing_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pharmatrust.manufacturing_system.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Find user by email for authentication
     * Note: Maps to 'username' column in database
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find all users by role
     */
    List<User> findByRole(User.Role role);

    /**
     * Find all active users by role
     */
    List<User> findByRoleAndIsActiveTrue(User.Role role);

    /**
     * Find user by manufacturer ID
     */
    Optional<User> findByManufacturerId(String manufacturerId);

    /**
     * Find all manufacturers
     */
    @Query("SELECT u FROM User u WHERE u.role = pharmatrust.manufacturing_system.entity.User$Role.MANUFACTURER AND u.isActive = true")
    List<User> findAllActiveManufacturers();

    /**
     * Find users by multiple roles (for approval workflow)
     */
    @Query("SELECT u FROM User u WHERE u.role IN :roles AND u.isActive = true")
    List<User> findByRolesAndActive(@Param("roles") List<User.Role> roles);
}
