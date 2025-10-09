-- ========================================================
-- FAST REAL EXAM DATA REPLACEMENT SCRIPT
-- Deletes existing exams and creates professional ones
-- ========================================================

-- STEP 1: CLEAN EXISTING DATA (CASCADE WILL HANDLE DEPENDENCIES)
SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM student_exams;
DELETE FROM exam_schedules;
DELETE FROM exams;
SET FOREIGN_KEY_CHECKS = 1;

-- STEP 2: CREATE REAL PROFESSIONAL EXAMS
INSERT INTO `exams` (`id`, `exam_name`, `duration`) VALUES
-- Computer Science & IT Exams
(1, 'Java Programming Certification', '3 hours'),
(2, 'Python Development Assessment', '2.5 hours'),
(3, 'JavaScript & React Fundamentals', '2 hours'),
(4, 'Database Design & SQL Mastery', '3 hours'),
(5, 'Data Structures & Algorithms', '3.5 hours'),
(6, 'Software Engineering Principles', '2.5 hours'),
(7, 'Web Development Full Stack', '4 hours'),
(8, 'Mobile App Development (Android/iOS)', '3 hours'),
(9, 'Cloud Computing (AWS/Azure)', '2.5 hours'),
(10, 'DevOps & CI/CD Pipeline', '3 hours'),

-- Cybersecurity & Networking
(11, 'Ethical Hacking & Penetration Testing', '4 hours'),
(12, 'Network Security Fundamentals', '2.5 hours'),
(13, 'Cybersecurity Risk Assessment', '3 hours'),
(14, 'Digital Forensics Investigation', '3.5 hours'),
(15, 'Information Security Management', '2 hours'),

-- Data Science & AI
(16, 'Machine Learning Algorithms', '3.5 hours'),
(17, 'Artificial Intelligence Applications', '3 hours'),
(18, 'Big Data Analytics with Python', '4 hours'),
(19, 'Deep Learning & Neural Networks', '4 hours'),
(20, 'Data Visualization & Business Intelligence', '2.5 hours'),

-- Business & Management
(21, 'Digital Marketing & SEO Strategy', '2 hours'),
(22, 'Project Management (PMP)', '3 hours'),
(23, 'Business Process Analysis', '2.5 hours'),
(24, 'Financial Management & Accounting', '3 hours'),
(25, 'Leadership & Team Management', '2 hours'),

-- Mathematics & Statistics
(26, 'Advanced Calculus & Linear Algebra', '3.5 hours'),
(27, 'Statistics & Probability Theory', '3 hours'),
(28, 'Discrete Mathematics for CS', '3 hours'),
(29, 'Applied Mathematics in Engineering', '3.5 hours'),
(30, 'Operations Research & Optimization', '3 hours'),

-- Design & Creative
(31, 'UI/UX Design Principles', '2.5 hours'),
(32, 'Graphic Design & Adobe Creative Suite', '3 hours'),
(33, 'Web Design & User Experience', '2.5 hours'),
(34, 'Digital Content Creation', '2 hours'),
(35, 'Brand Strategy & Visual Identity', '2 hours'),

-- Healthcare & Science
(36, 'Medical Terminology & Anatomy', '2.5 hours'),
(37, 'Nursing Fundamentals Assessment', '3 hours'),
(38, 'Pharmacology & Drug Administration', '3.5 hours'),
(39, 'Healthcare Information Systems', '2 hours'),
(40, 'Public Health & Epidemiology', '2.5 hours'),

-- Engineering
(41, 'Electrical Circuit Analysis', '3 hours'),
(42, 'Mechanical Engineering Design', '3.5 hours'),
(43, 'Civil Engineering Structures', '3 hours'),
(44, 'Environmental Engineering', '2.5 hours'),
(45, 'Industrial Engineering & Quality Control', '3 hours'),

-- Education & Training
(46, 'Educational Psychology & Learning Theory', '2.5 hours'),
(47, 'Curriculum Development & Assessment', '3 hours'),
(48, 'Classroom Management Strategies', '2 hours'),
(49, 'Special Education Needs Assessment', '2.5 hours'),
(50, 'Educational Technology Integration', '2 hours');

-- STEP 3: CREATE REALISTIC EXAM SCHEDULES
-- Week 1: October 14-18, 2025 (Morning & Afternoon Sessions)
INSERT INTO `exam_schedules` (`id`, `exam_id`, `room_id`, `scheduled_date`, `scheduled_time`, `capacity`) VALUES
-- Monday Oct 14 - Programming Day
(1, 1, 1, '2025-10-14', '09:00:00', 60),  -- Java Programming - Main Hall
(2, 2, 2, '2025-10-14', '09:00:00', 40),  -- Python Development - Room 101
(3, 3, 5, '2025-10-14', '13:00:00', 25),  -- JavaScript & React - Computer Lab 1
(4, 4, 6, '2025-10-14', '13:00:00', 25),  -- Database Design - Computer Lab 2

-- Tuesday Oct 15 - Algorithms & Engineering
(5, 5, 1, '2025-10-15', '09:00:00', 60),  -- Data Structures & Algorithms
(6, 6, 3, '2025-10-15', '09:00:00', 35),  -- Software Engineering
(7, 7, 2, '2025-10-15', '13:00:00', 40),  -- Web Development Full Stack
(8, 8, 5, '2025-10-15', '13:00:00', 25),  -- Mobile App Development

-- Wednesday Oct 16 - Cloud & DevOps
(9, 9, 1, '2025-10-16', '09:00:00', 60),   -- Cloud Computing
(10, 10, 4, '2025-10-16', '09:00:00', 30), -- DevOps & CI/CD
(11, 11, 2, '2025-10-16', '13:00:00', 40), -- Ethical Hacking
(12, 12, 3, '2025-10-16', '13:00:00', 35), -- Network Security

-- Thursday Oct 17 - Security & Forensics
(13, 13, 1, '2025-10-17', '09:00:00', 60), -- Cybersecurity Risk
(14, 14, 6, '2025-10-17', '09:00:00', 25), -- Digital Forensics
(15, 15, 4, '2025-10-17', '13:00:00', 30), -- Information Security
(16, 16, 5, '2025-10-17', '13:00:00', 25), -- Machine Learning

-- Friday Oct 18 - AI & Data Science
(17, 17, 1, '2025-10-18', '09:00:00', 60), -- Artificial Intelligence
(18, 18, 2, '2025-10-18', '09:00:00', 40), -- Big Data Analytics
(19, 19, 3, '2025-10-18', '13:00:00', 35), -- Deep Learning
(20, 20, 4, '2025-10-18', '13:00:00', 30), -- Data Visualization

-- Week 2: October 21-25, 2025 - Business & Specialized Exams
-- Monday Oct 21 - Business & Management
(21, 21, 2, '2025-10-21', '09:00:00', 40), -- Digital Marketing
(22, 22, 1, '2025-10-21', '09:00:00', 60), -- Project Management
(23, 23, 3, '2025-10-21', '13:00:00', 35), -- Business Process Analysis
(24, 24, 4, '2025-10-21', '13:00:00', 30), -- Financial Management

-- Tuesday Oct 22 - Mathematics & Statistics
(25, 26, 1, '2025-10-22', '09:00:00', 60), -- Advanced Calculus
(26, 27, 2, '2025-10-22', '09:00:00', 40), -- Statistics & Probability
(27, 28, 3, '2025-10-22', '13:00:00', 35), -- Discrete Mathematics
(28, 29, 4, '2025-10-22', '13:00:00', 30), -- Applied Mathematics

-- Wednesday Oct 23 - Design & Creative
(29, 31, 5, '2025-10-23', '09:00:00', 25), -- UI/UX Design
(30, 32, 6, '2025-10-23', '09:00:00', 25), -- Graphic Design
(31, 33, 2, '2025-10-23', '13:00:00', 40), -- Web Design
(32, 34, 3, '2025-10-23', '13:00:00', 35), -- Digital Content Creation

-- Thursday Oct 24 - Healthcare & Science
(33, 36, 1, '2025-10-24', '09:00:00', 60), -- Medical Terminology
(34, 37, 2, '2025-10-24', '09:00:00', 40), -- Nursing Fundamentals
(35, 38, 4, '2025-10-24', '13:00:00', 30), -- Pharmacology
(36, 40, 3, '2025-10-24', '13:00:00', 35), -- Public Health

-- Friday Oct 25 - Engineering & Education
(37, 41, 1, '2025-10-25', '09:00:00', 60), -- Electrical Circuit Analysis
(38, 42, 2, '2025-10-25', '09:00:00', 40), -- Mechanical Engineering
(39, 46, 3, '2025-10-25', '13:00:00', 35), -- Educational Psychology
(40, 47, 4, '2025-10-25', '13:00:00', 30); -- Curriculum Development

-- STEP 4: CREATE SAMPLE ENROLLMENTS FOR TESTING
INSERT INTO `student_exams` (`id`, `student_id`, `exam_schedule_id`, `status`, `is_paid`) VALUES
-- Student 1 (John Doe) - IT Professional Path
(1, 1, 1, 'Enrolled', 1),  -- Java Programming
(2, 1, 5, 'Enrolled', 1),  -- Data Structures & Algorithms
(3, 1, 9, 'Pending', 0),   -- Cloud Computing (not paid)

-- Student 2 (Jane Smith) - Data Science Path
(4, 2, 16, 'Enrolled', 1), -- Machine Learning
(5, 2, 17, 'Enrolled', 1), -- Artificial Intelligence
(6, 2, 18, 'Pending', 1),  -- Big Data Analytics

-- Student 3 (Carlos Reyes) - Cybersecurity Path
(7, 3, 11, 'Enrolled', 1), -- Ethical Hacking
(8, 3, 12, 'Enrolled', 1), -- Network Security
(9, 3, 13, 'Pending', 0),  -- Cybersecurity Risk

-- Student 4 (Maria Cruz) - Business Path
(10, 4, 21, 'Enrolled', 1), -- Digital Marketing
(11, 4, 22, 'Enrolled', 1), -- Project Management
(12, 4, 24, 'Pending', 1);  -- Financial Management

-- Reset AUTO_INCREMENT values
ALTER TABLE `exams` AUTO_INCREMENT = 51;
ALTER TABLE `exam_schedules` AUTO_INCREMENT = 41;
ALTER TABLE `student_exams` AUTO_INCREMENT = 13;

-- VERIFICATION QUERY
SELECT 
    'SUCCESS! Real exams created!' as status,
    (SELECT COUNT(*) FROM exams) as total_exams,
    (SELECT COUNT(*) FROM exam_schedules) as total_schedules,
    (SELECT COUNT(*) FROM student_exams) as total_enrollments;

-- Quick preview of created exams
SELECT id, exam_name, duration FROM exams ORDER BY id LIMIT 10;