package pharmatrust.manufacturing_system.repository;

import pharmatrust.manufacturing_system.entity.Complaint;
import pharmatrust.manufacturing_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, UUID> {
    List<Complaint> findByReporterOrderByCreatedAtDesc(User reporter);
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findBySerialNumber(String serialNumber);
}
