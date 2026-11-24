package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAdmin(admin: AdminEntity)

    @Query("SELECT * FROM admins WHERE cnic_normalized = :key OR mobile_normalized = :key LIMIT 1")
    suspend fun findByCnicOrMobile(key: String): AdminEntity?

    @Query("SELECT * FROM admins WHERE admin_uid = :adminUid LIMIT 1")
    suspend fun findByUid(adminUid: String): AdminEntity?

    @Query("SELECT * FROM admins WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): AdminEntity?

    @Query("SELECT * FROM admins ORDER BY created_at_epoch_ms DESC")
    fun observeAllAdmins(): Flow<List<AdminEntity>>

    @Query("SELECT * FROM admins WHERE sync_status = 'PENDING'")
    suspend fun getPendingAdmins(): List<AdminEntity>

    @Query("UPDATE admins SET authorization_status = :authStatus, authorized_by = :authorizedBy, sync_status = :syncStatus WHERE admin_uid = :adminUid")
    suspend fun updateAuthorizationStatus(
        adminUid: String,
        authStatus: String,
        authorizedBy: String?,
        syncStatus: String
    )

    @Query("UPDATE admins SET password_salt_b64 = :saltB64, password_hash_b64 = :hashB64 WHERE admin_uid = :adminUid")
    suspend fun updatePasswordVerifier(adminUid: String, saltB64: String, hashB64: String)

    @Query("SELECT * FROM admins WHERE (cnic_normalized = :key OR mobile_normalized = :key) AND admin_uid != :excludeAdminUid LIMIT 1")
    suspend fun findOtherByCnicOrMobile(key: String, excludeAdminUid: String): AdminEntity?

    @Query("SELECT * FROM admins WHERE email = :email AND admin_uid != :excludeAdminUid LIMIT 1")
    suspend fun findOtherByEmail(email: String, excludeAdminUid: String): AdminEntity?

    @Query("DELETE FROM admins WHERE admin_uid = :adminUid")
    suspend fun deleteByUid(adminUid: String)
}

@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStudentIgnore(student: StudentEntity): Long

    @Query("SELECT * FROM students WHERE class_name = :className AND LOWER(roll_number) = LOWER(:rollNumber) LIMIT 1")
    suspend fun findByClassAndRoll(className: String, rollNumber: String): StudentEntity?

    @Query("SELECT * FROM students WHERE qr_payload = :qrPayload LIMIT 1")
    suspend fun findByQrPayload(qrPayload: String): StudentEntity?

    @Query("SELECT * FROM students WHERE student_uid = :studentUid LIMIT 1")
    suspend fun findByUid(studentUid: String): StudentEntity?

    @Query("SELECT * FROM students ORDER BY class_name ASC, roll_number ASC")
    fun observeAllStudents(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY class_name ASC, roll_number ASC")
    suspend fun getAllStudentsOnce(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE sync_status = 'PENDING'")
    suspend fun getPendingStudents(): List<StudentEntity>

    @Query("UPDATE students SET sync_status = 'SYNCED' WHERE student_uid = :studentUid")
    suspend fun markStudentSynced(studentUid: String)

    @Update
    suspend fun updateStudent(student: StudentEntity)

    @Query("DELETE FROM students WHERE student_uid = :studentUid")
    suspend fun deleteStudentByUid(studentUid: String)

    @Query("SELECT COUNT(*) FROM students WHERE sync_status = 'PENDING'")
    fun observePendingStudentCount(): Flow<Int>
}

/**
 * STRICTLY APPEND-ONLY DAO.
 * Contains ZERO @Delete methods and ZERO DELETE queries to protect permanent attendance records.
 */
@Dao
interface AttendanceRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAttendanceAppendOnly(record: AttendanceRecordEntity): Long

    @Query("SELECT * FROM attendance_records WHERE student_uid = :studentUid AND attendance_date = :date LIMIT 1")
    suspend fun findByStudentAndDate(studentUid: String, date: String): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records ORDER BY marked_at_epoch_ms DESC")
    fun observeAllAttendance(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE student_uid = :studentUid ORDER BY marked_at_epoch_ms DESC")
    fun observeAttendanceForStudent(studentUid: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE attendance_date BETWEEN :startDate AND :endDate ORDER BY marked_at_epoch_ms DESC")
    suspend fun getRecordsInDateRange(startDate: String, endDate: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE sync_status = 'PENDING' ORDER BY marked_at_epoch_ms ASC LIMIT :limit")
    suspend fun getPendingAttendanceBatch(limit: Int = 25): List<AttendanceRecordEntity>

    @Query("UPDATE attendance_records SET sync_status = 'SYNCED', server_id = :serverId WHERE local_uid = :localUid")
    suspend fun markAttendanceSynced(localUid: String, serverId: String?)

    @Query("SELECT COUNT(*) FROM attendance_records WHERE sync_status = 'PENDING'")
    fun observePendingAttendanceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_records WHERE attendance_date = :date")
    fun observeCountForDate(date: String): Flow<Int>
}
