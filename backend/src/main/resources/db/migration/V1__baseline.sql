-- MySQL dump 10.13  Distrib 8.0.46, for Linux (x86_64)
--
-- Host: localhost    Database: ai_evaluate
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operator_id` bigint NOT NULL COMMENT '操作人',
  `module` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块：auth/user/task/submission/evaluation/report/system',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作（如LOGIN/REVIEW/EXPORT）',
  `target` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作对象标识',
  `detail` json DEFAULT NULL COMMENT '明细快照（脱敏后，S-08）',
  `ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端IP（兼容IPv6）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator_time` (`operator_id`,`create_time`),
  CONSTRAINT `fk_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表（仅追加，禁止UPDATE/DELETE，S-09）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,1,'system','CREATE_CLASS','class:2','{\"classCode\": \"M3-E2E-01\", \"className\": \"M3回归班\", \"teacherId\": 2}','172.18.0.1','2026-11-16 22:39:12'),(2,1,'system','UPDATE_CLASS','class:2','{\"className\": {\"after\": \"M3回归班-改\", \"before\": \"M3回归班\"}, \"teacherId\": {\"after\": 2, \"before\": 2}}','172.18.0.1','2026-11-16 22:39:12'),(3,1,'system','DELETE_CLASS','class:2','{\"classCode\": \"M3-E2E-01\", \"className\": \"M3回归班-改\"}','172.18.0.1','2026-11-16 22:39:12'),(4,1,'user','DISABLE_USER','user:7','{\"status\": 0, \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-16 22:39:16'),(5,1,'user','ENABLE_USER','user:7','{\"status\": 1, \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-16 22:39:16'),(6,1,'user','ASSIGN_ROLES','user:7','{\"after\": [\"TEACHER\"], \"before\": [\"STUDENT\"], \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-16 22:39:17'),(7,1,'user','ASSIGN_ROLES','user:7','{\"after\": [\"STUDENT\"], \"before\": [\"TEACHER\"], \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-16 22:39:17'),(8,1,'system','DISABLE_GOLDEN','golden:1','{\"active\": 0, \"sampleFileKey\": \"golden/golden_S01.docx\"}','172.18.0.1','2026-11-16 22:39:17'),(9,1,'system','ENABLE_GOLDEN','golden:1','{\"active\": 1, \"sampleFileKey\": \"golden/golden_S01.docx\"}','172.18.0.1','2026-11-16 22:39:17'),(10,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"modelName\": \"qwen2.5:7b\", \"timeoutSeconds\": 91}','172.18.0.1','2026-11-16 22:39:17'),(11,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"modelName\": \"qwen2.5:7b\", \"timeoutSeconds\": 90}','172.18.0.1','2026-11-16 22:39:17'),(12,1,'system','UPDATE_ROLE_PERMISSIONS','role:TEACHER','{\"after\": [\"audit:view\", \"auth:logout\", \"class:manage\", \"evaluation:history:view\", \"evaluation:re-evaluate\", \"evaluation:review\", \"evaluation:view\", \"report:export\", \"report:refresh\", \"report:view\", \"report:view:teacher\", \"submission:view\", \"task:create\", \"task:edit\", \"task:publish\", \"task:view\", \"user:me\"], \"before\": [\"auth:logout\", \"class:manage\", \"evaluation:history:view\", \"evaluation:re-evaluate\", \"evaluation:review\", \"evaluation:view\", \"report:export\", \"report:refresh\", \"report:view\", \"report:view:teacher\", \"submission:view\", \"task:create\", \"task:edit\", \"task:publish\", \"task:view\", \"user:me\"], \"roleCode\": \"TEACHER\"}','172.18.0.1','2026-11-16 22:39:21'),(13,1,'system','UPDATE_ROLE_PERMISSIONS','role:TEACHER','{\"after\": [\"auth:logout\", \"class:manage\", \"evaluation:history:view\", \"evaluation:re-evaluate\", \"evaluation:review\", \"evaluation:view\", \"report:export\", \"report:refresh\", \"report:view\", \"report:view:teacher\", \"submission:view\", \"task:create\", \"task:edit\", \"task:publish\", \"task:view\", \"user:me\"], \"before\": [\"audit:view\", \"auth:logout\", \"class:manage\", \"evaluation:history:view\", \"evaluation:re-evaluate\", \"evaluation:review\", \"evaluation:view\", \"report:export\", \"report:refresh\", \"report:view\", \"report:view:teacher\", \"submission:view\", \"task:create\", \"task:edit\", \"task:publish\", \"task:view\", \"user:me\"], \"roleCode\": \"TEACHER\"}','172.18.0.1','2026-11-16 22:39:21'),(14,1,'system','CREATE_CLASS','class:3','{\"classCode\": \"M3-UI-01\", \"className\": \"M3界面回归班\", \"teacherId\": 2}','172.18.0.1','2026-11-16 22:39:46'),(15,1,'system','DELETE_CLASS','class:3','{\"classCode\": \"M3-UI-01\", \"className\": \"M3界面回归班\"}','172.18.0.1','2026-11-16 22:39:54'),(16,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"modelName\": \"qwen2.5:7b\", \"timeoutSeconds\": 90}','172.18.0.1','2026-11-16 22:40:14'),(17,1,'system','UPDATE_CLASS','class:1','{\"className\": {\"after\": \"软件工程实训11班\", \"before\": \"软件工程实训11班\"}, \"teacherId\": {\"after\": 2, \"before\": 2}}','172.18.0.1','2026-11-16 23:17:51'),(18,1,'user','DISABLE_USER','user:4','{\"status\": 0, \"username\": \"newuser01\"}','172.18.0.1','2026-11-16 23:41:33'),(19,1,'user','ENABLE_USER','user:4','{\"status\": 1, \"username\": \"newuser01\"}','172.18.0.1','2026-11-16 23:41:35'),(20,3,'user','UPDATE_PROFILE','user:3','{\"phoneAfter\": \"138****0002\", \"phoneBefore\": \"138****0002\", \"realNameAfter\": \"示例学生·资料测试\", \"realNameBefore\": \"示例学生\"}','172.18.0.1','2026-11-16 23:44:12'),(21,3,'user','UPDATE_PROFILE','user:3','{\"phoneAfter\": \"138****0002\", \"phoneBefore\": \"138****0002\", \"realNameAfter\": \"示例学生\", \"realNameBefore\": \"示例学生·资料测试\"}','172.18.0.1','2026-11-16 23:44:17'),(22,1,'user','CREATE_USER','user:8','{\"classId\": null, \"realName\": \"CRUD测试\", \"username\": \"crud_e2e_01\", \"roleCodes\": [\"STUDENT\"]}','172.18.0.1','2026-11-16 23:51:11'),(23,1,'user','UPDATE_USER','user:8','{\"email\": {\"after\": \"c***@aies.local\", \"before\": \"c***@aies.local\"}, \"phone\": {\"after\": \"139****0010\", \"before\": \"139****0009\"}, \"classId\": {\"after\": null, \"before\": null}, \"realName\": {\"after\": \"CRUD测试改\", \"before\": \"CRUD测试\"}, \"username\": \"crud_e2e_01\"}','172.18.0.1','2026-11-16 23:51:19'),(24,1,'user','DELETE_USER','user:8','{\"realName\": \"CRUD测试改\", \"username\": \"crud_e2e_01\"}','172.18.0.1','2026-11-16 23:51:26'),(25,1,'system','HIDE_FILE','file:acc1cbb7e281440e982bc35fae23426a','{\"size\": 167, \"fileName\": \"report.pdf\", \"referenced\": true}','172.18.0.1','2026-11-17 00:33:37'),(26,2,'evaluation','RE_EVALUATE','evaluation:1','{\"operatorId\": 2, \"submissionId\": 5}',NULL,'2026-11-17 00:35:42'),(27,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"provider\": \"OLLAMA\", \"modelName\": \"qwen2.5:7b\", \"apiBaseUrl\": null, \"apiKeyHint\": null, \"apiKeyChanged\": false, \"timeoutSeconds\": 90}','172.18.0.1','2026-11-17 01:17:09'),(28,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"provider\": \"DEEPSEEK\", \"modelName\": \"first\", \"apiBaseUrl\": \"https://api.deepseek.com\", \"apiKeyHint\": \"67a8\", \"apiKeyChanged\": true, \"timeoutSeconds\": 90}','172.18.0.1','2026-11-17 01:27:50'),(29,1,'user','CREATE_USER','user:9','{\"classId\": null, \"realName\": \"示例教师二\", \"username\": \"teacher2\", \"roleCodes\": [\"TEACHER\"]}','172.18.0.1','2026-11-17 01:36:10'),(30,1,'user','UPDATE_USER','user:9','{\"email\": {\"after\": \"t***@aies.local\", \"before\": \"t***@aies.local\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": null}, \"realName\": {\"after\": \"王老师\", \"before\": \"示例教师二\"}, \"username\": \"teacher2\"}','172.18.0.1','2026-11-17 01:53:11'),(31,1,'user','UPDATE_USER','user:7','{\"email\": {\"after\": \"e***@aies.local\", \"before\": \"e***@aies.local\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": 1}, \"realName\": {\"after\": \"学生1\", \"before\": \"改密回归\"}, \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-17 01:53:32'),(32,1,'user','UPDATE_USER','user:6','{\"email\": {\"after\": \"s***@test.example\", \"before\": \"s***@test.example\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": 1}, \"realName\": {\"after\": \"学生2\", \"before\": \"走查学生\"}, \"username\": \"stu2026001\"}','172.18.0.1','2026-11-17 01:53:43'),(33,1,'user','UPDATE_USER','user:5','{\"email\": {\"after\": \"t***@aies.local\", \"before\": \"t***@aies.local\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": 1}, \"realName\": {\"after\": \"学生3\", \"before\": \"测试学生\"}, \"username\": \"2024999\"}','172.18.0.1','2026-11-17 01:54:04'),(34,1,'user','UPDATE_USER','user:4','{\"email\": {\"after\": \"t***@example.com\", \"before\": \"t***@example.com\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": 1}, \"realName\": {\"after\": \"学生4\", \"before\": \"测试学生\"}, \"username\": \"newuser01\"}','172.18.0.1','2026-11-17 01:54:16'),(35,1,'user','DELETE_USER','user:9','{\"realName\": \"王老师\", \"username\": \"teacher2\"}','172.18.0.1','2026-11-17 01:54:35'),(36,1,'user','DELETE_USER','user:7','{\"realName\": \"学生1\", \"username\": \"e2e_cp_01\"}','172.18.0.1','2026-11-17 01:54:37'),(37,1,'user','DELETE_USER','user:5','{\"realName\": \"学生3\", \"username\": \"2024999\"}','172.18.0.1','2026-11-17 01:54:44'),(38,1,'user','DELETE_USER','user:4','{\"realName\": \"学生4\", \"username\": \"newuser01\"}','172.18.0.1','2026-11-17 01:54:46'),(39,1,'user','UPDATE_USER','user:6','{\"email\": {\"after\": \"s***@test.example\", \"before\": \"s***@test.example\"}, \"phone\": {\"after\": \"\", \"before\": \"\"}, \"classId\": {\"after\": 1, \"before\": 1}, \"realName\": {\"after\": \"示例学生\", \"before\": \"学生2\"}, \"username\": \"stu2026001\"}','172.18.0.1','2026-11-17 01:55:14'),(40,1,'user','CREATE_USER','user:10','{\"classId\": 1, \"realName\": \"樊一可\", \"username\": \"2024110101\", \"roleCodes\": [\"STUDENT\"]}','172.18.0.1','2026-11-17 01:56:51'),(41,1,'user','CREATE_USER','user:11','{\"classId\": 1, \"realName\": \"周强强\", \"username\": \"2024110102\", \"roleCodes\": [\"STUDENT\"]}','172.18.0.1','2026-11-17 01:57:49'),(42,1,'user','CREATE_USER','user:12','{\"classId\": 1, \"realName\": \"阳宇恒\", \"username\": \"2024110103\", \"roleCodes\": [\"STUDENT\"]}','172.18.0.1','2026-11-17 01:58:33'),(43,1,'user','CREATE_USER','user:13','{\"classId\": 1, \"realName\": \"吴老师\", \"username\": \"19461101\", \"roleCodes\": [\"TEACHER\"]}','172.18.0.1','2026-11-17 02:00:42'),(44,1,'user','CREATE_USER','user:14','{\"classId\": 1, \"realName\": \"李老师\", \"username\": \"19461102\", \"roleCodes\": [\"TEACHER\"]}','172.18.0.1','2026-11-17 02:01:49'),(45,1,'system','HIDE_FILE','file:acc1cbb7e281440e982bc35fae23426a','{\"size\": 167, \"fileName\": \"report.pdf\", \"referenced\": true}','172.18.0.1','2026-11-17 02:02:30'),(46,1,'system','HIDE_FILE','file:1e4a2e1ddab34fdeb101844895d90f12','{\"size\": 167, \"fileName\": \"report.pdf\", \"referenced\": true}','172.18.0.1','2026-11-17 02:02:31'),(47,1,'system','HIDE_FILE','file:b4a01c95668b4e649abbd747671fd6ca','{\"size\": 167, \"fileName\": \"report.pdf\", \"referenced\": true}','172.18.0.1','2026-11-17 02:02:33'),(48,1,'system','HIDE_FILE','file:5b167d64f8934646b22c3bc6bb2edc10','{\"size\": 21, \"fileName\": \"note.docx\", \"referenced\": true}','172.18.0.1','2026-11-17 02:02:34'),(49,1,'system','UPDATE_ROLE_PERMISSIONS','role:STUDENT','{\"after\": [\"auth:logout\", \"evaluation:view\", \"report:view\", \"report:view:student\", \"submission:upload\", \"submission:view\", \"user:me\"], \"before\": [\"auth:logout\", \"evaluation:view\", \"report:view\", \"report:view:student\", \"submission:upload\", \"submission:view\", \"task:view\", \"user:me\"], \"roleCode\": \"STUDENT\"}','172.18.0.1','2026-11-17 09:27:09'),(50,1,'system','UPDATE_ROLE_PERMISSIONS','role:STUDENT','{\"after\": [\"auth:logout\", \"evaluation:view\", \"report:view\", \"report:view:student\", \"submission:upload\", \"submission:view\", \"task:view\", \"user:me\"], \"before\": [\"auth:logout\", \"evaluation:view\", \"report:view\", \"report:view:student\", \"submission:upload\", \"submission:view\", \"user:me\"], \"roleCode\": \"STUDENT\"}','172.18.0.1','2026-11-17 09:27:52'),(51,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"provider\": \"OLLAMA\", \"modelName\": \"qwen2.5:7b\", \"apiBaseUrl\": null, \"apiKeyHint\": \"67a8\", \"apiKeyChanged\": false, \"timeoutSeconds\": 90}','172.18.0.1','2026-11-17 09:36:22'),(52,1,'system','UPDATE_LLM_CONFIG','llm_config:1','{\"maxRetry\": 3, \"provider\": \"DEEPSEEK\", \"modelName\": \"deepseek-flash\", \"apiBaseUrl\": \"https://api.deepseek.com\", \"apiKeyHint\": \"67a8\", \"apiKeyChanged\": true, \"timeoutSeconds\": 90}','172.18.0.1','2026-11-17 09:37:32'),(53,2,'evaluation','RE_EVALUATE','evaluation:1','{\"operatorId\": 2, \"submissionId\": 5}',NULL,'2026-11-17 09:42:42'),(54,2,'evaluation','RE_EVALUATE','evaluation:1','{\"operatorId\": 2, \"submissionId\": 5}',NULL,'2026-11-17 09:47:18'),(55,2,'evaluation','RE_EVALUATE','evaluation:1','{\"operatorId\": 2, \"submissionId\": 5}',NULL,'2026-11-17 10:05:11'),(56,2,'evaluation','RE_EVALUATE','evaluation:4','{\"operatorId\": 2, \"submissionId\": 9}',NULL,'2026-11-17 10:10:19'),(57,2,'evaluation','RE_EVALUATE','evaluation:1','{\"operatorId\": 2, \"submissionId\": 5}',NULL,'2026-11-17 10:10:23'),(58,1,'evaluation','RE_EVALUATE','evaluation:4','{\"operatorId\": 1, \"submissionId\": 9}',NULL,'2026-11-17 11:13:24'),(59,1,'mq','REPLAY_PARSE','submission:6','{\"submissionId\": 6}','172.18.0.1','2026-11-17 11:27:16'),(60,1,'mq','REPLAY_PARSE','submission:6','{\"submissionId\": 6}','172.18.0.1','2026-11-17 11:30:45'),(61,1,'user','CREATE_USER','user:15','{\"classId\": null, \"realName\": \"Delete Test Teacher\", \"username\": \"del_teacher_test\", \"roleCodes\": [\"TEACHER\"]}','172.18.0.1','2026-11-17 12:40:38'),(63,1,'user','FORCE_DELETE_USER','user:15','{\"impact\": {\"userId\": 15, \"appeals\": 1, \"realName\": \"Delete Test Teacher\", \"username\": \"del_teacher_test\", \"auditLogs\": 1, \"roleCodes\": [\"TEACHER\"], \"ownedTasks\": 1, \"ownedClasses\": 1, \"totalRecords\": 8, \"reportExports\": 0, \"protectedReason\": \"\", \"unboundStudents\": 0, \"ownedSubmissions\": 1, \"protectedAccount\": false, \"reviewReferences\": 1, \"ownedEvaluationResults\": 1, \"ownedEvaluationHistories\": 1}, \"realName\": \"Delete Test Teacher\", \"username\": \"del_teacher_test\"}','172.18.0.1','2026-11-17 12:40:50'),(64,1,'user','CREATE_USER','user:16','{\"classId\": 1, \"realName\": \"Delete UI Test\", \"username\": \"del_ui_test\", \"roleCodes\": [\"STUDENT\"]}','172.18.0.1','2026-11-17 12:40:59'),(66,1,'user','FORCE_DELETE_USER','user:16','{\"impact\": {\"userId\": 16, \"appeals\": 0, \"realName\": \"Delete UI Test\", \"username\": \"del_ui_test\", \"auditLogs\": 1, \"roleCodes\": [\"STUDENT\"], \"ownedTasks\": 0, \"ownedClasses\": 0, \"totalRecords\": 1, \"reportExports\": 0, \"protectedReason\": \"\", \"unboundStudents\": 0, \"ownedSubmissions\": 0, \"protectedAccount\": false, \"reviewReferences\": 0, \"ownedEvaluationResults\": 0, \"ownedEvaluationHistories\": 0}, \"realName\": \"Delete UI Test\", \"username\": \"del_ui_test\"}','172.18.0.1','2026-11-17 12:41:18');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;

--
-- Table structure for table `chat_message`
--

DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sender_id` bigint NOT NULL COMMENT '发送人 sys_user.id',
  `receiver_id` bigint NOT NULL COMMENT '接收人 sys_user.id',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文本内容（1-1000字）',
  `read_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0未读 1已读',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_chat_sender` (`sender_id`,`receiver_id`,`id`),
  KEY `idx_chat_receiver_unread` (`receiver_id`,`read_flag`,`sender_id`),
  CONSTRAINT `fk_chat_receiver` FOREIGN KEY (`receiver_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_chat_sender` FOREIGN KEY (`sender_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在线聊天消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_message`
--

/*!40000 ALTER TABLE `chat_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `chat_message` ENABLE KEYS */;

--
-- Table structure for table `class`
--

DROP TABLE IF EXISTS `class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `class_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班级名称（03-02待补录字段，最小假设）',
  `class_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '班级码（UC-43注册绑定/分发）',
  `teacher_id` bigint NOT NULL COMMENT '绑定教师（ER §8.1：class→sys_user）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_code` (`class_code`),
  KEY `fk_class_teacher` (`teacher_id`),
  CONSTRAINT `fk_class_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级表（绑定教师）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class`
--

/*!40000 ALTER TABLE `class` DISABLE KEYS */;
INSERT INTO `class` VALUES (1,'软件工程实训11班','AIES11-2025',2,'2026-11-15 22:34:52');
/*!40000 ALTER TABLE `class` ENABLE KEYS */;

--
-- Table structure for table `evaluation_appeal`
--

DROP TABLE IF EXISTS `evaluation_appeal`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation_appeal` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `evaluation_id` bigint NOT NULL COMMENT '评价id',
  `submission_id` bigint NOT NULL COMMENT '提交id（冗余）',
  `student_id` bigint NOT NULL COMMENT '申诉学生',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申诉理由',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
  `assigned_teacher_id` bigint DEFAULT NULL COMMENT '随机改派教师',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appeal_eval` (`evaluation_id`),
  KEY `idx_appeal_teacher_status` (`assigned_teacher_id`,`status`),
  KEY `fk_appeal_submission` (`submission_id`),
  KEY `fk_appeal_student` (`student_id`),
  CONSTRAINT `fk_appeal_eval` FOREIGN KEY (`evaluation_id`) REFERENCES `evaluation_result` (`id`),
  CONSTRAINT `fk_appeal_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_appeal_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`),
  CONSTRAINT `fk_appeal_teacher` FOREIGN KEY (`assigned_teacher_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学生评价申诉与改派复评';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `evaluation_appeal`
--

/*!40000 ALTER TABLE `evaluation_appeal` DISABLE KEYS */;
INSERT INTO `evaluation_appeal` VALUES (2,1,5,3,'1','PENDING',14,'2026-11-17 01:32:14',NULL),(3,4,9,3,'1','PENDING',14,'2026-11-17 02:09:56',NULL);
/*!40000 ALTER TABLE `evaluation_appeal` ENABLE KEYS */;

--
-- Table structure for table `evaluation_history`
--

DROP TABLE IF EXISTS `evaluation_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `evaluation_id` bigint NOT NULL COMMENT '=evaluation_result.id',
  `submission_id` bigint NOT NULL COMMENT '提交id（冗余，提交维度可查）',
  `version_no` int NOT NULL COMMENT '版本号：1=AI初评，2+=复核/重评',
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典：AI_INITIAL/TEACHER_REVIEW/RE_EVALUATE（TEACHER_REVIEW时teacher_comment必填≥10字→应用层30007）',
  `total_score` decimal(5,2) NOT NULL COMMENT '总分快照',
  `metric_scores` json NOT NULL COMMENT '各维度得分快照',
  `ai_comment` text COLLATE utf8mb4_unicode_ci COMMENT 'AI评语快照',
  `ai_model` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 版本模型标识（教师版本为空）',
  `teacher_comment` text COLLATE utf8mb4_unicode_ci COMMENT '复核理由',
  `reviewer_id` bigint DEFAULT NULL COMMENT '教师（AI版本为NULL）',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI版本关联留痕对象key',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '版本生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_eval_version` (`evaluation_id`,`version_no`),
  KEY `idx_submission` (`submission_id`),
  KEY `fk_hist_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_hist_evaluation` FOREIGN KEY (`evaluation_id`) REFERENCES `evaluation_result` (`id`),
  CONSTRAINT `fk_hist_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价历史版本表（ADR-004，只读红线：无更新/删除接口，写请求→40301）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `evaluation_history`
--

/*!40000 ALTER TABLE `evaluation_history` DISABLE KEYS */;
INSERT INTO `evaluation_history` VALUES (1,1,5,1,'AI_INITIAL',58.00,'{\"代码质量\": 19, \"文档规范\": 16, \"功能正确性\": 23}','评语：该作业在功能实现上存在一定的问题，代码质量有待提高，文档规范方面也存在不足。具体分析如下：\n\n1. **功能正确性（23/40）**：作业中直接在Controller里拼SQL插入borrow表，这种做法存在安全隐患，容易导致SQL注入等问题。此外，库存判断写在页面上，这不符合MVC设计模式的要求，应该将业务逻辑与视图分离。运行时偶尔报空指针的问题也表明代码的健壮性不足，需要进一步优化。虽然作业提到了后续会修复，但目前的实现方式存在较大的风险。\n\n2. **代码质量（19/35）**：代码中直接拼接SQL语句，缺乏参数化处理，这不仅容易导致SQL注入，还可能引发其他安全问题。此外，代码中没有进行充分的异常处理，导致运行时偶尔出现空指针异常。代码结构不够清晰，没有遵循良好的编程习惯，如缺少注释、变量命名不够规范等。这些都影响了代码的可读性和可维护性。\n\n3. **文档规范（16/25）**：作业中没有提供详细的文档说明，如缺少对系统功能、模块设计、接口说明等的描述。文档中也没有对代码中的关键部分进行注释，使得阅读者难以理解代码的逻辑。此外，文档格式也不够规范，缺少必要的标题、段落划分等。\n\n总体来看，该作业在功能实现上存在一定的问题，代码质量有待提高，文档规范方面也存在不足。建议在后续开发中，加强对SQL安全性的考虑，遵循MVC设计模式，提高代码的健壮性和可维护性。同时，增加详细的文档说明，提高代码的可读性和可理解性。',NULL,NULL,NULL,'946bbc91d1a4415d9645d58f89420c36','2026-11-16 01:45:41'),(2,1,5,2,'TEACHER_REVIEW',62.00,'{\"代码质量\": 21, \"文档规范\": 16, \"功能正确性\": 25}','评语：该作业在功能实现上存在一定的问题，代码质量有待提高，文档规范方面也存在不足。具体分析如下：\n\n1. **功能正确性（23/40）**：作业中直接在Controller里拼SQL插入borrow表，这种做法存在安全隐患，容易导致SQL注入等问题。此外，库存判断写在页面上，这不符合MVC设计模式的要求，应该将业务逻辑与视图分离。运行时偶尔报空指针的问题也表明代码的健壮性不足，需要进一步优化。虽然作业提到了后续会修复，但目前的实现方式存在较大的风险。\n\n2. **代码质量（19/35）**：代码中直接拼接SQL语句，缺乏参数化处理，这不仅容易导致SQL注入，还可能引发其他安全问题。此外，代码中没有进行充分的异常处理，导致运行时偶尔出现空指针异常。代码结构不够清晰，没有遵循良好的编程习惯，如缺少注释、变量命名不够规范等。这些都影响了代码的可读性和可维护性。\n\n3. **文档规范（16/25）**：作业中没有提供详细的文档说明，如缺少对系统功能、模块设计、接口说明等的描述。文档中也没有对代码中的关键部分进行注释，使得阅读者难以理解代码的逻辑。此外，文档格式也不够规范，缺少必要的标题、段落划分等。\n\n总体来看，该作业在功能实现上存在一定的问题，代码质量有待提高，文档规范方面也存在不足。建议在后续开发中，加强对SQL安全性的考虑，遵循MVC设计模式，提高代码的健壮性和可维护性。同时，增加详细的文档说明，提高代码的可读性和可理解性。',NULL,'功能实现完整度尚可，边界处理需加强，综合调整为六十二分',2,NULL,'2026-11-16 17:25:25'),(3,1,5,3,'AI_INITIAL',62.00,'{\"代码质量\": 22, \"文档规范\": 15, \"功能正确性\": 25}','根据提供的作业文本，可以明显看出该作业在多个方面存在不足，具体评语如下：\n\n1. **功能正确性（25/40）**：\n   - 作业中直接在Controller里拼SQL插入borrow表，这种做法存在严重的安全隐患，容易导致SQL注入攻击。此外，库存判断写在页面上，这不符合现代Web开发的最佳实践，容易导致业务逻辑混乱和代码难以维护。\n   - 运行时偶尔报空指针的问题，说明代码中存在未处理的异常情况，这在实际开发中是不可接受的。虽然作者提到后续会修复，但目前的代码状态已经影响了功能的正确性。\n   - 缺乏测试，这使得在实际运行中可能会遇到更多未预见的问题，增加了维护成本。\n\n2. **代码质量（22/35）**：\n   - 代码结构混乱，直接在Controller中处理数据库操作，违反了分层设计原则，使得代码难以维护和扩展。\n   - 缺乏注释和文档，代码可读性差，不利于团队协作和后续维护。\n   - 代码中存在潜在的安全风险，如SQL注入，这在实际开发中是必须避免的。\n\n3. **文档规范（15/25）**：\n   - 作业中没有提供任何文档说明，包括项目结构、模块功能、接口说明等，这使得其他开发者难以理解代码逻辑。\n   - 缺乏必要的注释，代码中的关键逻辑没有解释，增加了理解难度。\n   - 代码格式不规范，没有遵循统一的编码规范，影响代码的可读性。\n\n总体来看，该作业的质量较低，存在多个严重的问题，需要进行大量的改进。建议重新设计代码结构，采用ORM框架进行数据库操作，增加单元测试，完善文档说明，提高代码质量和可维护性。',NULL,NULL,NULL,'8aba533796f6463999c8df7edb93ec57','2026-11-17 00:36:03'),(4,1,5,4,'TEACHER_REVIEW',90.00,'{\"代码质量\": 50, \"文档规范\": 15, \"功能正确性\": 25}','根据提供的作业文本，可以明显看出该作业在多个方面存在不足，具体评语如下：\n\n1. **功能正确性（25/40）**：\n   - 作业中直接在Controller里拼SQL插入borrow表，这种做法存在严重的安全隐患，容易导致SQL注入攻击。此外，库存判断写在页面上，这不符合现代Web开发的最佳实践，容易导致业务逻辑混乱和代码难以维护。\n   - 运行时偶尔报空指针的问题，说明代码中存在未处理的异常情况，这在实际开发中是不可接受的。虽然作者提到后续会修复，但目前的代码状态已经影响了功能的正确性。\n   - 缺乏测试，这使得在实际运行中可能会遇到更多未预见的问题，增加了维护成本。\n\n2. **代码质量（22/35）**：\n   - 代码结构混乱，直接在Controller中处理数据库操作，违反了分层设计原则，使得代码难以维护和扩展。\n   - 缺乏注释和文档，代码可读性差，不利于团队协作和后续维护。\n   - 代码中存在潜在的安全风险，如SQL注入，这在实际开发中是必须避免的。\n\n3. **文档规范（15/25）**：\n   - 作业中没有提供任何文档说明，包括项目结构、模块功能、接口说明等，这使得其他开发者难以理解代码逻辑。\n   - 缺乏必要的注释，代码中的关键逻辑没有解释，增加了理解难度。\n   - 代码格式不规范，没有遵循统一的编码规范，影响代码的可读性。\n\n总体来看，该作业的质量较低，存在多个严重的问题，需要进行大量的改进。建议重新设计代码结构，采用ORM框架进行数据库操作，增加单元测试，完善文档说明，提高代码质量和可维护性。',NULL,'没毛11111111',2,NULL,'2026-11-17 01:11:33'),(7,1,5,5,'AI_INITIAL',47.00,'{\"代码质量\": 17, \"文档规范\": 10, \"功能正确性\": 20}','该作业属于不及格档。功能正确性方面，核心借阅功能存在明显缺陷：库存判断被放在页面层，Controller直接拼接SQL插入borrow表，业务逻辑与数据访问边界混乱；且运行时偶发空指针异常尚未修复，说明基本功能稳定性和正确性不足。代码质量方面，直接在Controller中拼SQL易引发SQL注入、维护困难和一致性问题，缺少测试，异常处理缺失，代码结构与工程规范较差。文档规范方面，除标题和问题描述外，几乎没有需求说明、设计说明、接口说明、测试记录和部署运行文档，规范性严重不足。建议优先将SQL与业务逻辑下沉到Service/DAO层，使用参数化查询，补充库存校验与异常处理，并补齐最小可用测试和项目文档。','deepseek-flash',NULL,NULL,'c90fb57a0f3340c1921eb0e33a03282e','2026-11-17 09:42:49'),(8,1,5,6,'TEACHER_REVIEW',47.00,'{\"代码质量\": 17, \"文档规范\": 10, \"功能正确性\": 20}','该作业属于不及格档。功能正确性方面，核心借阅功能存在明显缺陷：库存判断被放在页面层，Controller直接拼接SQL插入borrow表，业务逻辑与数据访问边界混乱；且运行时偶发空指针异常尚未修复，说明基本功能稳定性和正确性不足。代码质量方面，直接在Controller中拼SQL易引发SQL注入、维护困难和一致性问题，缺少测试，异常处理缺失，代码结构与工程规范较差。文档规范方面，除标题和问题描述外，几乎没有需求说明、设计说明、接口说明、测试记录和部署运行文档，规范性严重不足。建议优先将SQL与业务逻辑下沉到Service/DAO层，使用参数化查询，补充库存校验与异常处理，并补齐最小可用测试和项目文档。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 09:47:14'),(9,1,5,7,'AI_INITIAL',41.00,'{\"代码质量\": 15, \"文档规范\": 8, \"功能正确性\": 18}','该作业属于明显低于不及格档的待改进作业。功能正确性方面，核心业务逻辑不完整，库存判断写在页面、直接在Controller中拼接SQL插入borrow表，存在安全与一致性问题，且运行时偶发空指针未修复，可靠性不足。代码质量方面，分层与职责混乱，SQL直接写在Controller中，缺少参数化处理、异常处理与必要测试，可维护性和健壮性较差。文档规范方面，基本没有设计说明、接口说明、测试记录和问题修复记录，文档严重不足。建议按MVC分层重构，将库存校验和借阅逻辑放入Service，使用参数化SQL或ORM，补充异常处理、单元测试与完整项目文档后再提交。','deepseek-flash',NULL,NULL,'74809b1f22714ff0a14131c91c76acf8','2026-11-17 09:47:44'),(10,1,5,8,'TEACHER_REVIEW',41.00,'{\"代码质量\": 15, \"文档规范\": 8, \"功能正确性\": 18}','该作业属于明显低于不及格档的待改进作业。功能正确性方面，核心业务逻辑不完整，库存判断写在页面、直接在Controller中拼接SQL插入borrow表，存在安全与一致性问题，且运行时偶发空指针未修复，可靠性不足。代码质量方面，分层与职责混乱，SQL直接写在Controller中，缺少参数化处理、异常处理与必要测试，可维护性和健壮性较差。文档规范方面，基本没有设计说明、接口说明、测试记录和问题修复记录，文档严重不足。建议按MVC分层重构，将库存校验和借阅逻辑放入Service，使用参数化SQL或ORM，补充异常处理、单元测试与完整项目文档后再提交。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 10:05:07'),(11,1,5,9,'AI_INITIAL',40.00,'{\"代码质量\": 14, \"文档规范\": 6, \"功能正确性\": 20}','该作业属于不及格档中质量明显偏低的样本。功能上仅能完成最基础的图书管理流程，库存判断放在页面且直接在Controller中拼接SQL，业务规则分散，存在明显正确性与安全风险；运行时偶发空指针且未修复，未编写测试，稳定性不足。代码质量方面缺乏合理分层，SQL与控制器耦合，可维护性和可扩展性差。文档仅有标题，缺少需求说明、设计说明、接口说明、测试记录和运行说明，规范性严重不足。综合判定为低质量不及格作业，总分40分。','deepseek-flash',NULL,NULL,'6909a060b4294752b8a01f95f9a4b24a','2026-11-17 10:05:20'),(12,4,9,1,'AI_INITIAL',93.00,'{\"代码质量\": 33, \"文档规范\": 22, \"功能正确性\": 38}','该作业功能覆盖注册登录、商品发布、检索、订单提交与状态管理，并包含验证码、库存校验、重复提交拦截和事务处理，功能完整且正确性较强。代码采用Controller-Service-Mapper分层，使用参数化SQL、统一异常处理、事务和乐观锁，工程规范性和并发安全意识较好；前端也有校验与统一接口封装。文档说明了表结构、接口、核心流程和部署步骤，测试场景较完整并记录了问题修复。整体达到优秀作业档位，但在高并发边界、测试覆盖深度和文档细节上仍有小幅提升空间。','deepseek-flash',NULL,NULL,'a0384468d2f04dc3867372f45cb56523','2026-11-17 10:07:51'),(13,4,9,2,'TEACHER_REVIEW',93.00,'{\"代码质量\": 33, \"文档规范\": 22, \"功能正确性\": 38}','该作业功能覆盖注册登录、商品发布、检索、订单提交与状态管理，并包含验证码、库存校验、重复提交拦截和事务处理，功能完整且正确性较强。代码采用Controller-Service-Mapper分层，使用参数化SQL、统一异常处理、事务和乐观锁，工程规范性和并发安全意识较好；前端也有校验与统一接口封装。文档说明了表结构、接口、核心流程和部署步骤，测试场景较完整并记录了问题修复。整体达到优秀作业档位，但在高并发边界、测试覆盖深度和文档细节上仍有小幅提升空间。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 10:09:28'),(14,1,5,10,'TEACHER_REVIEW',40.00,'{\"代码质量\": 14, \"文档规范\": 6, \"功能正确性\": 20}','该作业属于不及格档中质量明显偏低的样本。功能上仅能完成最基础的图书管理流程，库存判断放在页面且直接在Controller中拼接SQL，业务规则分散，存在明显正确性与安全风险；运行时偶发空指针且未修复，未编写测试，稳定性不足。代码质量方面缺乏合理分层，SQL与控制器耦合，可维护性和可扩展性差。文档仅有标题，缺少需求说明、设计说明、接口说明、测试记录和运行说明，规范性严重不足。综合判定为低质量不及格作业，总分40分。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 10:09:30'),(15,4,9,3,'AI_INITIAL',93.00,'{\"代码质量\": 33, \"文档规范\": 22, \"功能正确性\": 38}','作业整体达到优秀档水平。需求覆盖注册登录、商品发布与检索、订单提交及状态管理，并考虑验证码、库存校验、重复提交拦截、事务处理等关键点，功能设计较完整；后端采用分层架构，使用参数化SQL、统一异常处理、事务与乐观锁，前端有校验与统一接口封装，代码质量较高；文档包含表结构、接口清单、核心流程、部署步骤及测试场景，规范性和可追溯性较好。不足之处是作业文本以概括性描述为主，缺少关键实现代码、测试用例与结果数据等细节佐证，若能补充并发控制、边界条件和异常场景的具体验证记录，可进一步提升至更优水平。','deepseek-flash',NULL,NULL,'cddf5da6d6e74a01abc4ec40f7382d67','2026-11-17 10:10:23'),(16,1,5,11,'AI_INITIAL',45.00,'{\"代码质量\": 16, \"文档规范\": 9, \"功能正确性\": 20}','该作业属于不及格档。功能方面仅实现在Controller中直接拼SQL插入borrow表，库存判断放在页面层，核心业务校验不可靠，且存在偶发空指针未修复，功能正确性与稳定性不足。代码质量方面存在严重的分层与安全问题：Controller承担SQL拼接和数据访问职责，缺少Service/DAO分层，未写测试，异常处理缺失，可维护性差。文档规范方面仅有标题，缺少需求说明、设计说明、接口说明、测试记录和问题修复记录，文档严重不足。综合评定为45分，处于不及格档附近。','deepseek-flash',NULL,NULL,'d00bec4d2d7d4ab780522c075450c183','2026-11-17 10:10:28'),(17,4,9,4,'TEACHER_REVIEW',93.00,'{\"代码质量\": 33, \"文档规范\": 22, \"功能正确性\": 38}','作业整体达到优秀档水平。需求覆盖注册登录、商品发布与检索、订单提交及状态管理，并考虑验证码、库存校验、重复提交拦截、事务处理等关键点，功能设计较完整；后端采用分层架构，使用参数化SQL、统一异常处理、事务与乐观锁，前端有校验与统一接口封装，代码质量较高；文档包含表结构、接口清单、核心流程、部署步骤及测试场景，规范性和可追溯性较好。不足之处是作业文本以概括性描述为主，缺少关键实现代码、测试用例与结果数据等细节佐证，若能补充并发控制、边界条件和异常场景的具体验证记录，可进一步提升至更优水平。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 11:12:42'),(18,4,9,5,'AI_INITIAL',92.00,'{\"代码质量\": 32, \"文档规范\": 23, \"功能正确性\": 37}','该作业功能覆盖注册登录、商品发布与检索、订单提交及状态管理，包含图形验证码、库存校验、重复提交拦截和事务处理，核心业务闭环较完整，功能正确性达到优秀档水平。代码采用Controller-Service-Mapper分层，使用参数化SQL、统一异常处理、事务和乐观锁版本号，并配合前端校验与接口封装，质量较高，但文本仅作概述，缺少关键代码片段、并发控制细节及异常边界说明。文档说明了数据库表结构、接口清单、核心流程和部署步骤，测试覆盖登录、发布、提交订单、重复提交、库存不足等场景，并记录分页越界与空数据异常修复，规范较完整，但未展示测试用例数据、执行结果截图或部署验证记录。综合判定为优秀档附近，建议补充实现证据与测试细节。','deepseek-flash',NULL,NULL,'c11928be86b343c58a5a357f162b4286','2026-11-17 11:13:29'),(20,4,9,6,'TEACHER_REVIEW',92.00,'{\"代码质量\": 32, \"文档规范\": 23, \"功能正确性\": 37}','该作业功能覆盖注册登录、商品发布与检索、订单提交及状态管理，包含图形验证码、库存校验、重复提交拦截和事务处理，核心业务闭环较完整，功能正确性达到优秀档水平。代码采用Controller-Service-Mapper分层，使用参数化SQL、统一异常处理、事务和乐观锁版本号，并配合前端校验与接口封装，质量较高，但文本仅作概述，缺少关键代码片段、并发控制细节及异常边界说明。文档说明了数据库表结构、接口清单、核心流程和部署步骤，测试覆盖登录、发布、提交订单、重复提交、库存不足等场景，并记录分页越界与空数据异常修复，规范较完整，但未展示测试用例数据、执行结果截图或部署验证记录。综合判定为优秀档附近，建议补充实现证据与测试细节。',NULL,'教师一键通过 AI 评价结果',2,NULL,'2026-11-17 12:55:47');
/*!40000 ALTER TABLE `evaluation_history` ENABLE KEYS */;

--
-- Table structure for table `evaluation_result`
--

DROP TABLE IF EXISTS `evaluation_result`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `evaluation_result` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（=evaluation_history.evaluation_id）',
  `submission_id` bigint NOT NULL COMMENT '提交id（每提交唯一行，1:1）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '评价状态机字典：PENDING/AI_DONE/REVIEWED/FAILED（§7.1，状态迁移03-04 §4.1）',
  `current_version_no` int NOT NULL COMMENT '当前生效版本号（报表统计仅读此表）',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT '总分（FAILED时为NULL）',
  `metric_scores` json DEFAULT NULL COMMENT '各指标得分快照',
  `ai_comment` text COLLATE utf8mb4_unicode_ci COMMENT 'AI评语',
  `ai_model` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次 AI 评价模型标识',
  `reviewer_id` bigint DEFAULT NULL COMMENT '最近复核教师',
  `review_time` datetime DEFAULT NULL COMMENT '最近复核时间',
  `error_code` int DEFAULT NULL COMMENT '失败码（FAILED时记录，如40003）',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联冷bucket留痕（AI版本）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁@Version字段（§3.5）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_result_submission` (`submission_id`),
  KEY `fk_result_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_result_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_result_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价当前值表（ADR-004）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `evaluation_result`
--

/*!40000 ALTER TABLE `evaluation_result` DISABLE KEYS */;
INSERT INTO `evaluation_result` VALUES (1,5,'AI_DONE',11,45.00,'{\"代码质量\": 16, \"文档规范\": 9, \"功能正确性\": 20}','该作业属于不及格档。功能方面仅实现在Controller中直接拼SQL插入borrow表，库存判断放在页面层，核心业务校验不可靠，且存在偶发空指针未修复，功能正确性与稳定性不足。代码质量方面存在严重的分层与安全问题：Controller承担SQL拼接和数据访问职责，缺少Service/DAO分层，未写测试，异常处理缺失，可维护性差。文档规范方面仅有标题，缺少需求说明、设计说明、接口说明、测试记录和问题修复记录，文档严重不足。综合评定为45分，处于不及格档附近。','deepseek-flash',NULL,NULL,NULL,'d00bec4d2d7d4ab780522c075450c183',17,'2026-11-17 12:03:05'),(4,9,'REVIEWED',6,92.00,'{\"代码质量\": 32, \"文档规范\": 23, \"功能正确性\": 37}','该作业功能覆盖注册登录、商品发布与检索、订单提交及状态管理，包含图形验证码、库存校验、重复提交拦截和事务处理，核心业务闭环较完整，功能正确性达到优秀档水平。代码采用Controller-Service-Mapper分层，使用参数化SQL、统一异常处理、事务和乐观锁版本号，并配合前端校验与接口封装，质量较高，但文本仅作概述，缺少关键代码片段、并发控制细节及异常边界说明。文档说明了数据库表结构、接口清单、核心流程和部署步骤，测试覆盖登录、发布、提交订单、重复提交、库存不足等场景，并记录分页越界与空数据异常修复，规范较完整，但未展示测试用例数据、执行结果截图或部署验证记录。综合判定为优秀档附近，建议补充实现证据与测试细节。','deepseek-flash',2,'2026-11-17 12:55:47',NULL,'c11928be86b343c58a5a357f162b4286',7,'2026-11-17 12:55:47');
/*!40000 ALTER TABLE `evaluation_result` ENABLE KEYS */;

--
-- Table structure for table `golden_dataset`
--

DROP TABLE IF EXISTS `golden_dataset`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `golden_dataset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sample_file_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '样本文件MinIO热bucket对象key（golden/前缀）',
  `teacher_total` decimal(5,2) NOT NULL COMMENT '教师标注总分',
  `teacher_metrics` json NOT NULL COMMENT '教师标注各指标分',
  `active` tinyint NOT NULL DEFAULT '1' COMMENT '1=参与回归；0=停用不参与',
  `last_consistency` decimal(4,3) DEFAULT NULL COMMENT '最近回归一致率（单样本|AI-教师|≤总分×5%判一致；总体≥70%，NFR-03）',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='黄金数据集样本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `golden_dataset`
--

/*!40000 ALTER TABLE `golden_dataset` DISABLE KEYS */;
INSERT INTO `golden_dataset` VALUES (1,'golden/golden_S01.docx',95.00,'{\"代码质量\": 34, \"文档规范\": 22, \"功能正确性\": 39}',1,1.000),(2,'golden/golden_S02.docx',94.00,'{\"代码质量\": 33, \"文档规范\": 23, \"功能正确性\": 38}',1,1.000),(3,'golden/golden_S03.docx',93.00,'{\"代码质量\": 34, \"文档规范\": 21, \"功能正确性\": 38}',1,1.000),(4,'golden/golden_S04.docx',90.00,'{\"代码质量\": 32, \"文档规范\": 21, \"功能正确性\": 37}',1,1.000),(5,'golden/golden_S05.docx',80.00,'{\"代码质量\": 28, \"文档规范\": 17, \"功能正确性\": 35}',1,1.000),(6,'golden/golden_S06.docx',75.00,'{\"代码质量\": 26, \"文档规范\": 15, \"功能正确性\": 34}',1,1.000),(7,'golden/golden_S07.docx',78.00,'{\"代码质量\": 27, \"文档规范\": 18, \"功能正确性\": 33}',1,1.000),(8,'golden/golden_S08.docx',73.00,'{\"代码质量\": 25, \"文档规范\": 16, \"功能正确性\": 32}',1,1.000),(9,'golden/golden_S09.docx',60.00,'{\"代码质量\": 20, \"文档规范\": 12, \"功能正确性\": 28}',1,1.000),(10,'golden/golden_S10.docx',54.00,'{\"代码质量\": 18, \"文档规范\": 10, \"功能正确性\": 26}',1,0.000),(11,'golden/golden_S11.docx',50.00,'{\"代码质量\": 16, \"文档规范\": 9, \"功能正确性\": 25}',1,0.000),(12,'golden/golden_S12.docx',47.00,'{\"代码质量\": 14, \"文档规范\": 9, \"功能正确性\": 24}',1,0.000),(13,'golden/golden_S013.docx',92.00,'{\"代码质量\": 33, \"文档规范\": 21, \"功能正确性\": 38}',1,1.000),(14,'golden/golden_S014.docx',90.00,'{\"代码质量\": 32, \"文档规范\": 21, \"功能正确性\": 37}',1,1.000),(15,'golden/golden_S015.docx',82.00,'{\"代码质量\": 29, \"文档规范\": 20, \"功能正确性\": 33}',1,0.000),(16,'golden/golden_S016.docx',76.00,'{\"代码质量\": 27, \"文档规范\": 18, \"功能正确性\": 31}',1,1.000),(17,'golden/golden_S017.docx',70.00,'{\"代码质量\": 24, \"文档规范\": 17, \"功能正确性\": 29}',1,1.000),(18,'golden/golden_S018.docx',54.00,'{\"代码质量\": 19, \"文档规范\": 13, \"功能正确性\": 22}',1,0.000),(19,'golden/golden_S019.docx',48.00,'{\"代码质量\": 17, \"文档规范\": 11, \"功能正确性\": 20}',1,0.000),(20,'golden/golden_S020.docx',44.00,'{\"代码质量\": 16, \"文档规范\": 10, \"功能正确性\": 18}',1,0.000);
/*!40000 ALTER TABLE `golden_dataset` ENABLE KEYS */;

--
-- Table structure for table `llm_config`
--

DROP TABLE IF EXISTS `llm_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `llm_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（单行）',
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OLLAMA' COMMENT '供应商：OLLAMA/OPENAI/DEEPSEEK/DASHSCOPE/MOONSHOT/ZHIPU/CUSTOM',
  `api_base_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'OpenAI 兼容 Base URL（OLLAMA 可空，取 ai.ollama.base-url）',
  `api_key_cipher` varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AES-GCM 加密后的 API Key（密文）',
  `api_key_hint` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'API Key 尾号提示（仅展示用）',
  `model_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名（本地Ollama 7B，ADR-001数据不出域）',
  `temperature` decimal(2,1) NOT NULL DEFAULT '0.0' COMMENT '锁定0（复现性，仅展示不可改，NFR-03一致性前置）',
  `timeout_seconds` int NOT NULL DEFAULT '60' COMMENT '调用超时（NFR-01）',
  `max_retry` int NOT NULL DEFAULT '3' COMMENT 'Schema校验失败重试上限（指数退避1s/2s/4s）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_llm_temperature` CHECK ((`temperature` = 0.0))
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM运行参数表（单行）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `llm_config`
--

/*!40000 ALTER TABLE `llm_config` DISABLE KEYS */;
INSERT INTO `llm_config` VALUES (1,'DEEPSEEK','https://api.deepseek.com','v1:LVJ7SPLiuape38VoKJFzxNZsDNVlqA5RLwr0mleMU5Vk30YJR1tgKD/ZcVeIUJ7OmcKrruCWmq97NobfpVch','67a8','deepseek-flash',0.0,90,3,'2026-11-17 09:37:32');
/*!40000 ALTER TABLE `llm_config` ENABLE KEYS */;

--
-- Table structure for table `prompt_template`
--

DROP TABLE IF EXISTS `prompt_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prompt_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `version_no` int NOT NULL COMMENT '模板版本号（evaluation_history经version_no逻辑关联，非FK）',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '系统Prompt模板（含指标/作业内容占位符）',
  `enabled` tinyint NOT NULL DEFAULT '0' COMMENT '1=启用（全局唯一，应用层保证）；UC-38读取启用版',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_version` (`version_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Prompt模板版本表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `prompt_template`
--

/*!40000 ALTER TABLE `prompt_template` DISABLE KEYS */;
INSERT INTO `prompt_template` VALUES (1,1,'你是软件实训作业评阅助手。请依据以下评价指标对作业文本进行评分：{metrics}。评分要求：1)每项指标分值不得超过其权重上限；2)各指标分之和必须等于总分；3)总分为0~100的数值。作业提取文本：{parsed_text}。仅输出一个JSON对象，禁止输出markdown代码块围栏、解释或任何其他文字：{\"total_score\":数值,\"metric_scores\":{\"指标名\":分数},\"ai_comment\":\"评语（≤2000字）\"}。评分须与教师历史评分风格一致（一致性回归≥70%，NFR-03）。',0,'2026-11-15 22:34:52'),(2,2,'你是软件实训作业评阅助手。请依据以下评价指标对作业文本进行评分：{metrics}。教师终评标定的参考档位：优秀作业≈93分，中等作业≈73分，待改进作业≈60分，不及格作业≈47分。评分方法：先对比参考档位判断作业属于哪一档，再给出该档附近的分数；作业质量明显更差给40~55，明显更好给93~98。不同质量的作业分数必须拉开差距，严禁全部集中在65~80。每项指标分不得超过其权重上限，各指标分之和必须等于总分，总分0~100。作业提取文本：{parsed_text}。仅输出一个JSON对象，禁止输出markdown代码块围栏、解释或任何其他文字：{\"total_score\":数值,\"metric_scores\":{\"指标名\":分数},\"ai_comment\":\"评语（≤2000字）\"}。',1,'2026-11-16 11:43:54');
/*!40000 ALTER TABLE `prompt_template` ENABLE KEYS */;

--
-- Table structure for table `report_export_task`
--

DROP TABLE IF EXISTS `report_export_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_export_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键（Header Idempotency-Key，Redis SETNX TTL 10min联动）',
  `requester_id` bigint NOT NULL COMMENT '发起人',
  `filter_json` json DEFAULT NULL COMMENT '筛选条件',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUEUED' COMMENT '字典：QUEUED/GENERATING/DONE/FAILED（ENUM→VARCHAR翻译）',
  `file_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '结果PDF的MinIO对象key（DONE时非空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `fk_report_requester` (`requester_id`),
  CONSTRAINT `fk_report_requester` FOREIGN KEY (`requester_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报表导出任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report_export_task`
--

/*!40000 ALTER TABLE `report_export_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `report_export_task` ENABLE KEYS */;

--
-- Table structure for table `submission`
--

DROP TABLE IF EXISTS `submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '任务id（超截止→40002，应用层校验）',
  `student_id` bigint NOT NULL COMMENT '学生id（仅本人，IDOR防线S-05）',
  `file_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO热bucket对象key（UUID重命名，S-07）',
  `file_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名（展示用）',
  `file_size` bigint NOT NULL COMMENT '文件字节：≤20MB上限由应用层校验（→30003）；DB只做正数CHECK（T5）',
  `file_type` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典：docx/pdf 白名单（否则→30001）',
  `version_no` int NOT NULL COMMENT '提交版本：同任务同人递增，重复上传覆盖展示、旧版保留',
  `parse_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '解析状态机字典：PENDING/QUEUED/SUCCESS/FAILED（§7.1 V1.2四态）',
  `parsed_text_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提取文本MinIO对象key（文本不入DB，W3决策）',
  `canceled` tinyint NOT NULL DEFAULT '0' COMMENT '1=学生已取消该次提交',
  `cancel_time` datetime DEFAULT NULL COMMENT '取消时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（截止校验基准）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_student_version` (`task_id`,`student_id`,`version_no`),
  KEY `idx_task_student` (`task_id`,`student_id`),
  KEY `fk_sub_student` (`student_id`),
  CONSTRAINT `fk_sub_student` FOREIGN KEY (`student_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_sub_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
  CONSTRAINT `chk_submission_file_size` CHECK ((`file_size` > 0)),
  CONSTRAINT `chk_submission_file_type` CHECK ((`file_type` in (_utf8mb4'docx',_utf8mb4'pdf')))
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表（版本化）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission`
--

/*!40000 ALTER TABLE `submission` DISABLE KEYS */;
INSERT INTO `submission` VALUES (1,2,3,'1e4a2e1ddab34fdeb101844895d90f12','report.pdf',167,'pdf',1,'PENDING',NULL,1,'2026-11-16 17:39:46','2026-11-15 23:38:38'),(2,2,3,'b4a01c95668b4e649abbd747671fd6ca','report.pdf',167,'pdf',2,'PENDING',NULL,1,'2026-11-16 17:39:45','2026-11-15 23:38:38'),(3,2,3,'5b167d64f8934646b22c3bc6bb2edc10','note.docx',21,'docx',3,'PENDING',NULL,1,'2026-11-16 17:39:43','2026-11-15 23:38:38'),(4,3,3,'acc1cbb7e281440e982bc35fae23426a','report.pdf',167,'pdf',1,'PENDING',NULL,1,'2026-11-16 17:39:48','2026-11-15 23:38:39'),(5,2,3,'d5ae9624b8794518a9bc1281819f06ae','golden_S09.docx',2536,'docx',4,'SUCCESS','parsed/c43d54b3b4ad47f08f2e68e5b02648a0.txt',0,NULL,'2026-11-16 00:11:21'),(6,2,6,'7f6559dc332c4702b0cf18fbbe3cdc07','test-report.pdf',24,'pdf',1,'FAILED',NULL,0,NULL,'2026-11-16 21:28:18'),(9,2,3,'9c34cbf7b60044c29992e22c4455bd59','mq-demo-submission.docx',1624,'docx',5,'SUCCESS','parsed/0deaf6850ad040fc9c635edecd0496fc.txt',0,NULL,'2026-11-17 10:07:48');
/*!40000 ALTER TABLE `submission` ENABLE KEYS */;

--
-- Table structure for table `sys_permission`
--

DROP TABLE IF EXISTS `sys_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `perm_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限点编码（与02-04 RTM一一对应，S-06）',
  `perm_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限点名称',
  `module` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属模块：auth/user/task/submission/evaluation/report/system',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_permission`
--

/*!40000 ALTER TABLE `sys_permission` DISABLE KEYS */;
INSERT INTO `sys_permission` VALUES (1,'auth:logout','退出登录','auth','2026-11-15 22:34:51'),(2,'user:me','查看本人信息','user','2026-11-15 22:34:51'),(3,'user:manage','用户管理','user','2026-11-15 22:34:51'),(4,'task:create','创建任务','task','2026-11-15 22:34:51'),(5,'task:publish','发布任务','task','2026-11-15 22:34:51'),(6,'task:edit','编辑任务','task','2026-11-15 22:34:51'),(7,'task:view','查看任务','task','2026-11-15 22:34:51'),(8,'submission:upload','上传提交','submission','2026-11-15 22:34:51'),(9,'submission:view','查看提交','submission','2026-11-15 22:34:51'),(10,'evaluation:view','查看评价','evaluation','2026-11-15 22:34:51'),(11,'evaluation:review','复核评价','evaluation','2026-11-15 22:34:51'),(12,'evaluation:re-evaluate','触发重评','evaluation','2026-11-15 22:34:51'),(13,'evaluation:history:view','查看评价历史版本','evaluation','2026-11-15 22:34:51'),(14,'report:view','查看报表','report','2026-11-15 22:34:51'),(15,'report:export','导出报表','report','2026-11-15 22:34:51'),(16,'role:manage','角色管理','system','2026-11-15 22:34:51'),(17,'system:config','系统参数配置','system','2026-11-15 22:34:51'),(18,'class:manage','班级管理','system','2026-11-15 22:34:51'),(19,'audit:view','审计日志查询','system','2026-11-15 22:34:51'),(20,'llm:config','LLM参数配置','system','2026-11-15 22:34:51'),(21,'prompt:manage','Prompt模板管理','system','2026-11-15 22:34:51'),(22,'golden:manage','黄金数据集管理','system','2026-11-15 22:34:51'),(23,'report:view:teacher','教师报表查看','report','2026-11-16 17:14:59'),(24,'report:view:student','学生个人报表查看','report','2026-11-16 17:14:59'),(25,'report:refresh','报表缓存刷新','report','2026-11-16 17:14:59'),(26,'chat:use','在线聊天','chat','2026-11-24 09:16:43');
/*!40000 ALTER TABLE `sys_permission` ENABLE KEYS */;

--
-- Table structure for table `sys_role`
--

DROP TABLE IF EXISTS `sys_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码：STUDENT/TEACHER/ADMIN',
  `role_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `built_in` tinyint NOT NULL DEFAULT '1' COMMENT '内置角色=1不可删（UC-31负例）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表（内置三角色）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role`
--

/*!40000 ALTER TABLE `sys_role` DISABLE KEYS */;
INSERT INTO `sys_role` VALUES (1,'STUDENT','学生',1,'2026-11-15 22:34:51'),(2,'TEACHER','教师',1,'2026-11-15 22:34:51'),(3,'ADMIN','管理员',1,'2026-11-15 22:34:51');
/*!40000 ALTER TABLE `sys_role` ENABLE KEYS */;

--
-- Table structure for table `sys_role_permission`
--

DROP TABLE IF EXISTS `sys_role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色id',
  `perm_id` bigint NOT NULL COMMENT '权限点id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`role_id`,`perm_id`),
  KEY `fk_rp_perm` (`perm_id`),
  CONSTRAINT `fk_rp_perm` FOREIGN KEY (`perm_id`) REFERENCES `sys_permission` (`id`),
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_role_permission`
--

/*!40000 ALTER TABLE `sys_role_permission` DISABLE KEYS */;
INSERT INTO `sys_role_permission` VALUES (98,1,1),(99,1,2),(100,1,7),(101,1,8),(102,1,9),(103,1,10),(104,1,14),(105,1,24),(106,1,26),(75,2,1),(76,2,2),(77,2,4),(78,2,5),(79,2,6),(80,2,7),(81,2,9),(82,2,10),(83,2,11),(84,2,12),(85,2,13),(86,2,14),(87,2,15),(88,2,18),(89,2,23),(90,2,25),(107,2,26),(2,3,1),(22,3,2),(21,3,3),(17,3,4),(19,3,5),(18,3,6),(20,3,7),(14,3,8),(15,3,9),(7,3,10),(6,3,11),(5,3,12),(4,3,13),(12,3,14),(11,3,15),(13,3,16),(16,3,17),(3,3,18),(1,3,19),(9,3,20),(10,3,21),(8,3,22);
/*!40000 ALTER TABLE `sys_role_permission` ENABLE KEYS */;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学号/工号，登录名',
  `password` varchar(72) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'bcrypt哈希（强度10），不存明文',
  `real_name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名（PII，展示不脱敏，日志脱敏）',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号（PII，展示脱敏138****）',
  `email` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱（UC-42/44前置，未绑定→30014）',
  `class_id` bigint DEFAULT NULL COMMENT '学生所属班级（外键V2补加）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用/0禁用（禁用→40103）',
  `first_login` tinyint NOT NULL DEFAULT '1' COMMENT '首登强制改密标记 1是/0否',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `fk_user_class` (`class_id`),
  CONSTRAINT `fk_user_class` FOREIGN KEY (`class_id`) REFERENCES `class` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主表（RBAC五表之一）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (1,'admin','$2b$10$FNuJ8YwoYbouSl3WWV6Czu5zoDpp8ar4LlmvZpyQbhtQreDawM71u','系统管理员','13800000000',NULL,NULL,1,0,'2026-11-15 22:34:51','2026-11-16 01:58:09'),(2,'teacher','$2b$10$FNuJ8YwoYbouSl3WWV6Czu5zoDpp8ar4LlmvZpyQbhtQreDawM71u','示例教师','13800000001',NULL,NULL,1,0,'2026-11-15 22:34:51','2026-11-16 01:58:09'),(3,'student','$2b$10$FNuJ8YwoYbouSl3WWV6Czu5zoDpp8ar4LlmvZpyQbhtQreDawM71u','示例学生','13800000002',NULL,1,1,0,'2026-11-15 22:34:51','2026-11-16 23:44:17'),(6,'stu2026001','$2a$10$kQbLah5epq/32NRMNeL19ODVI8y94ga1ReU9rqZisoiEvNX57F28m','示例学生',NULL,'stu2026001@test.example',1,1,1,'2026-11-16 21:27:31','2026-11-17 01:55:14'),(10,'2024110101','$2a$10$ocmeTeAIrgV/xGFKhw.JneF8bgkLnQ/0hTRs6NLxupAbssRsIBbPC','樊一可',NULL,'001Stu@qq.com',1,1,1,'2026-11-17 01:56:51','2026-11-17 01:56:51'),(11,'2024110102','$2a$10$PMlGF3Vs.z9IBGoIP3nPnurtfmEG0JJlXFsE7aRf3zKUxrfORThnS','周强强',NULL,'002Stu@qq.com',1,1,1,'2026-11-17 01:57:49','2026-11-17 01:57:49'),(12,'2024110103','$2a$10$0BXgjcEsTfaKL2i7PzcukO9Ado2aXCl.VuUYt20CoaQnpfZ70583K','阳宇恒',NULL,'003Stu@qq.com',1,1,1,'2026-11-17 01:58:33','2026-11-17 01:58:33'),(13,'19461101','$2a$10$QA2ZUx85qkbmuiLSFaRu8.zebPiGX3oCjc72zL2z15YV5GlfvVgLm','吴老师',NULL,'001Tea@qq.com',1,1,1,'2026-11-17 02:00:42','2026-11-17 02:00:42'),(14,'19461102','$2a$10$KdWQwalj9rAD4.iFqi56luktrbJApYHtWktbiMcftc8QFLPLvS30.','李老师',NULL,'002Tea@qq.com',1,1,1,'2026-11-17 02:01:49','2026-11-17 02:01:49');
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;

--
-- Table structure for table `sys_user_role`
--

DROP TABLE IF EXISTS `sys_user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `role_id` bigint NOT NULL COMMENT '角色id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `fk_ur_role` (`role_id`),
  CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`),
  CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-角色关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user_role`
--

/*!40000 ALTER TABLE `sys_user_role` DISABLE KEYS */;
INSERT INTO `sys_user_role` VALUES (1,1,3),(2,2,2),(3,3,1),(6,6,1),(12,10,1),(13,11,1),(14,12,1),(15,13,2),(16,14,2);
/*!40000 ALTER TABLE `sys_user_role` ENABLE KEYS */;

--
-- Table structure for table `task`
--

DROP TABLE IF EXISTS `task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '任务描述',
  `teacher_id` bigint NOT NULL COMMENT '发布教师（ER §8.1）',
  `class_id` bigint NOT NULL COMMENT '目标班级（ER §8.1）',
  `deadline` datetime NOT NULL COMMENT '截止时间（早于当前→30006，应用层校验）',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '字典：DRAFT/PUBLISHED/CLOSED（发布后指标锁定，应用层拦截）',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间（DRAFT 为空）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_teacher` (`teacher_id`),
  KEY `idx_class` (`class_id`),
  KEY `idx_deadline` (`deadline`),
  CONSTRAINT `fk_task_class` FOREIGN KEY (`class_id`) REFERENCES `class` (`id`),
  CONSTRAINT `fk_task_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task`
--

/*!40000 ALTER TABLE `task` DISABLE KEYS */;
INSERT INTO `task` VALUES (2,'C2发布实弹任务','publish flow',2,1,'2026-11-22 23:28:02','CLOSED','2026-11-15 23:28:02','2026-11-15 23:28:02'),(3,'C2任务2','admin publish',2,1,'2026-11-15 00:00:00','CLOSED','2026-11-15 23:29:47','2026-11-15 23:29:47'),(8,'任务1','任务测试',2,1,'2026-11-30 00:00:00','PUBLISHED','2026-11-17 01:13:20','2026-11-17 09:13:14');
/*!40000 ALTER TABLE `task` ENABLE KEYS */;

--
-- Table structure for table `task_metric`
--

DROP TABLE IF EXISTS `task_metric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_metric` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '所属任务',
  `metric_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '指标名（如正确性/完整性/规范性，2~64字符）',
  `weight` decimal(5,2) NOT NULL COMMENT '指标权重：0<w≤100；单任务Σ=100应用层事务校验（≠100→40001），不做DB跨行约束（03-02 §3.3勘误）',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示序',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_metric` (`task_id`,`metric_name`),
  CONSTRAINT `fk_metric_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`),
  CONSTRAINT `chk_metric_weight` CHECK (((`weight` > 0) and (`weight` <= 100)))
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价指标表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_metric`
--

/*!40000 ALTER TABLE `task_metric` DISABLE KEYS */;
INSERT INTO `task_metric` VALUES (4,2,'功能正确性',40.00,1),(5,2,'代码质量',35.00,2),(6,2,'文档规范',25.00,3),(7,3,'功能分',60.00,1),(8,3,'质量分',40.00,2),(26,8,'指标一',50.00,1),(27,8,'指标2',50.00,2);
/*!40000 ALTER TABLE `task_metric` ENABLE KEYS */;

--
-- Table structure for table `trace_compensation`
--

DROP TABLE IF EXISTS `trace_compensation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `trace_compensation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评价traceId（与冷桶对象key一一对应）',
  `object_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '冷桶对象key（trace/{traceId}.json）',
  `payload` json NOT NULL COMMENT '留痕JSON原文（补写用：LLM原始响应+耗时+模型版本+校验结果全量）',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '字典：PENDING/DONE（补写成功置DONE）',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '补写重试次数（≥10保留PENDING人工兜底）',
  `last_error` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近一次补写失败原因（截断512）',
  `next_retry_time` datetime NOT NULL COMMENT '下次重试时间（指数退避2^(n-1)分钟，上限60分钟）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comp_trace` (`trace_id`),
  KEY `idx_comp_status_next` (`status`,`next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='留痕写冷桶补偿表（V6，定时任务重试补写）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `trace_compensation`
--

/*!40000 ALTER TABLE `trace_compensation` DISABLE KEYS */;
/*!40000 ALTER TABLE `trace_compensation` ENABLE KEYS */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed
