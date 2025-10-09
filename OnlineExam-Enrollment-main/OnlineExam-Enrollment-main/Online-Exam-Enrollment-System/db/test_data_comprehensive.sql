-- ====================================================================
-- COMPREHENSIVE TEST DATA SCRIPT FOR EXAM ENROLLMENT SYSTEM
-- Compatible with exam_enrollment (8).sql schema
-- ====================================================================

-- First, let's add more comprehensive exam data
INSERT INTO `exams` (`id`, `exam_name`, `duration`) VALUES
(101, 'Advanced Mathematics', '3 hours'),
(102, 'Computer Programming Fundamentals', '2.5 hours'),
(103, 'Database Management Systems', '2 hours'),
(104, 'Web Development', '3 hours'),
(105, 'Data Structures and Algorithms', '3 hours'),
(106, 'Software Engineering', '2.5 hours'),
(107, 'Network Security', '2 hours'),
(108, 'Mobile App Development', '3 hours'),
(109, 'Artificial Intelligence', '2.5 hours'),
(110, 'Machine Learning', '3 hours'),
(111, 'Cloud Computing', '2 hours'),
(112, 'DevOps Fundamentals', '2.5 hours'),
(113, 'Cybersecurity Essentials', '2 hours'),
(114, 'Big Data Analytics', '3 hours'),
(115, 'Internet of Things', '2 hours'),
(116, 'Blockchain Technology', '2.5 hours'),
(117, 'Digital Marketing', '2 hours'),
(118, 'Project Management', '2.5 hours'),
(119, 'Business Analytics', '2 hours'),
(120, 'Financial Accounting', '3 hours'),
(121, 'Statistics and Probability', '2.5 hours'),
(122, 'Technical Writing', '2 hours'),
(123, 'System Administration', '2.5 hours'),
(124, 'Quality Assurance Testing', '2 hours'),
(125, 'User Experience Design', '2.5 hours');

-- Add more comprehensive room data
INSERT INTO `rooms` (`id`, `room_name`, `capacity`) VALUES
(7, 'Lecture Hall A', 100),
(8, 'Lecture Hall B', 80),
(9, 'Conference Room 1', 20),
(10, 'Conference Room 2', 15),
(11, 'Laboratory 1', 30),
(12, 'Laboratory 2', 30);

-- Add more students for testing
INSERT INTO `students` (`id`, `name`, `email`, `password`, `balance`, `course_id`) VALUES
(11, 'Alice Johnson', 'alice@example.com', '1234', 2000.00, 1),
(12, 'Bob Wilson', 'bob@example.com', '1234', 1800.00, 2),
(13, 'Catherine Brown', 'catherine@example.com', '1234', 1600.00, 1),
(14, 'David Lee', 'david@example.com', '1234', 1400.00, 3),
(15, 'Emma Davis', 'emma@example.com', '1234', 1900.00, 2),
(16, 'Frank Miller', 'frank@example.com', '1234', 1700.00, 4),
(17, 'Grace Taylor', 'grace@example.com', '1234', 1500.00, 5),
(18, 'Henry Anderson', 'henry@example.com', '1234', 1300.00, 1),
(19, 'Ivy Thomas', 'ivy@example.com', '1234', 2100.00, 3),
(20, 'Jack Robinson', 'jack@example.com', '1234', 1750.00, 2);

-- Create comprehensive exam schedules for the new exams
-- Week 1: October 14-18, 2025
INSERT INTO `exam_schedules` (`id`, `exam_id`, `room_id`, `scheduled_date`, `scheduled_time`, `capacity`) VALUES
-- Monday, October 14, 2025
(51, 101, 7, '2025-10-14', '09:00:00', 100),
(52, 102, 1, '2025-10-14', '11:00:00', 60),
(53, 103, 2, '2025-10-14', '13:00:00', 40),
(54, 104, 3, '2025-10-14', '15:00:00', 35),

-- Tuesday, October 15, 2025
(55, 105, 8, '2025-10-15', '09:00:00', 80),
(56, 106, 4, '2025-10-15', '11:00:00', 30),
(57, 107, 5, '2025-10-15', '13:00:00', 25),
(58, 108, 6, '2025-10-15', '15:00:00', 25),

-- Wednesday, October 16, 2025
(59, 109, 7, '2025-10-16', '09:00:00', 100),
(60, 110, 11, '2025-10-16', '11:00:00', 30),
(61, 111, 12, '2025-10-16', '13:00:00', 30),
(62, 112, 1, '2025-10-16', '15:00:00', 60),

-- Thursday, October 17, 2025
(63, 113, 2, '2025-10-17', '09:00:00', 40),
(64, 114, 8, '2025-10-17', '11:00:00', 80),
(65, 115, 3, '2025-10-17', '13:00:00', 35),
(66, 116, 4, '2025-10-17', '15:00:00', 30),

-- Friday, October 18, 2025
(67, 117, 9, '2025-10-18', '09:00:00', 20),
(68, 118, 10, '2025-10-18', '11:00:00', 15),
(69, 119, 5, '2025-10-18', '13:00:00', 25),
(70, 120, 6, '2025-10-18', '15:00:00', 25),

-- Week 2: October 21-25, 2025
-- Monday, October 21, 2025
(71, 121, 7, '2025-10-21', '09:00:00', 100),
(72, 122, 1, '2025-10-21', '11:00:00', 60),
(73, 123, 2, '2025-10-21', '13:00:00', 40),
(74, 124, 3, '2025-10-21', '15:00:00', 35),

-- Tuesday, October 22, 2025
(75, 125, 8, '2025-10-22', '09:00:00', 80),
(76, 101, 4, '2025-10-22', '11:00:00', 30), -- Second session of Advanced Math
(77, 102, 5, '2025-10-22', '13:00:00', 25), -- Second session of Programming
(78, 103, 6, '2025-10-22', '15:00:00', 25), -- Second session of Database

-- Create additional exam schedules for existing exams (51-100) to provide more options
-- Wednesday, October 23, 2025
(79, 51, 7, '2025-10-23', '09:00:00', 100),
(80, 52, 1, '2025-10-23', '11:00:00', 60),
(81, 53, 2, '2025-10-23', '13:00:00', 40),
(82, 54, 3, '2025-10-23', '15:00:00', 35),

-- Thursday, October 24, 2025
(83, 55, 8, '2025-10-24', '09:00:00', 80),
(84, 56, 4, '2025-10-24', '11:00:00', 30),
(85, 57, 5, '2025-10-24', '13:00:00', 25),
(86, 58, 6, '2025-10-24', '15:00:00', 25);

-- Add some test enrollments to demonstrate the system
INSERT INTO `student_exams` (`id`, `student_id`, `exam_schedule_id`, `status`, `is_paid`) VALUES
-- Student 1 (John Doe) enrollments
(11, 1, 51, 'Enrolled', 1),  -- Advanced Mathematics
(12, 1, 55, 'Enrolled', 1),  -- Data Structures and Algorithms
(13, 1, 59, 'Pending', 0),   -- Artificial Intelligence (not paid yet)

-- Student 2 (Jane Smith) enrollments
(14, 2, 52, 'Enrolled', 1),  -- Computer Programming Fundamentals
(15, 2, 56, 'Enrolled', 1),  -- Software Engineering
(16, 2, 60, 'Enrolled', 1),  -- Machine Learning

-- Student 3 (Carlos Reyes) enrollments
(17, 3, 53, 'Enrolled', 1),  -- Database Management Systems
(18, 3, 57, 'Enrolled', 1),  -- Network Security
(19, 3, 61, 'Pending', 0),   -- Cloud Computing (not paid yet)

-- Student 11 (Alice Johnson) enrollments
(20, 11, 54, 'Enrolled', 1), -- Web Development
(21, 11, 58, 'Enrolled', 1), -- Mobile App Development
(22, 11, 62, 'Enrolled', 1), -- DevOps Fundamentals

-- Student 12 (Bob Wilson) enrollments
(23, 12, 63, 'Enrolled', 1), -- Cybersecurity Essentials
(24, 12, 67, 'Enrolled', 1), -- Digital Marketing
(25, 12, 71, 'Pending', 0),  -- Statistics and Probability (not paid yet)

-- Add more time slots for better scheduling
INSERT INTO `time_slots` (`id`, `start_time`) VALUES
(5, '08:00:00'),
(6, '10:00:00'),
(7, '12:00:00'),
(8, '14:00:00'),
(9, '16:00:00'),
(10, '17:00:00');

-- Update exam AUTO_INCREMENT to handle new exams
ALTER TABLE `exams` AUTO_INCREMENT = 126;

-- Update exam_schedules AUTO_INCREMENT
ALTER TABLE `exam_schedules` AUTO_INCREMENT = 87;

-- Update student_exams AUTO_INCREMENT
ALTER TABLE `student_exams` AUTO_INCREMENT = 26;

-- Update students AUTO_INCREMENT
ALTER TABLE `students` AUTO_INCREMENT = 21;

-- Update rooms AUTO_INCREMENT
ALTER TABLE `rooms` AUTO_INCREMENT = 13;

-- Update time_slots AUTO_INCREMENT
ALTER TABLE `time_slots` AUTO_INCREMENT = 11;

-- ====================================================================
-- VERIFICATION QUERIES - Run these to test the data
-- ====================================================================

-- Check total exams available
-- SELECT COUNT(*) as total_exams FROM exams;

-- Check total exam schedules
-- SELECT COUNT(*) as total_schedules FROM exam_schedules;

-- Check students with their enrollments
-- SELECT s.name, COUNT(se.id) as enrolled_exams 
-- FROM students s 
-- LEFT JOIN student_exams se ON s.id = se.student_id 
-- GROUP BY s.id, s.name 
-- ORDER BY enrolled_exams DESC;

-- Check room utilization
-- SELECT r.room_name, COUNT(es.id) as scheduled_exams 
-- FROM rooms r 
-- LEFT JOIN exam_schedules es ON r.id = es.room_id 
-- GROUP BY r.id, r.room_name 
-- ORDER BY scheduled_exams DESC;

-- Check upcoming exam schedules with enrollment counts
-- SELECT e.exam_name, es.scheduled_date, es.scheduled_time, r.room_name, 
--        es.capacity, COUNT(se.id) as enrolled_students
-- FROM exam_schedules es
-- JOIN exams e ON es.exam_id = e.id
-- JOIN rooms r ON es.room_id = r.id
-- LEFT JOIN student_exams se ON es.id = se.exam_schedule_id
-- WHERE es.scheduled_date >= CURDATE()
-- GROUP BY es.id
-- ORDER BY es.scheduled_date, es.scheduled_time;

-- ====================================================================
-- SUCCESS MESSAGE
-- ====================================================================
SELECT 'Test data inserted successfully!' as status,
       (SELECT COUNT(*) FROM exams) as total_exams,
       (SELECT COUNT(*) FROM exam_schedules) as total_schedules,
       (SELECT COUNT(*) FROM student_exams) as total_enrollments,
       (SELECT COUNT(*) FROM students) as total_students,
       (SELECT COUNT(*) FROM rooms) as total_rooms;