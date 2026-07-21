/*
SQLyog Ultimate v11.33 (64 bit)
MySQL - 5.7.11-log : Database - yulinlin_admin
*********************************************************************
*/  

/*!40101 SET NAMES utf8 */;

/*!40101 SET SQL_MODE=''*/;

/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
CREATE DATABASE /*!32312 IF NOT EXISTS*/`yulinlin_admin` /*!40100 DEFAULT CHARACTER SET utf8mb4 */;

USE `yulinlin_admin`;

/*Table structure for table `ad_content` */

DROP TABLE IF EXISTS `ad_content`;

CREATE TABLE `ad_content` (
  `id` varchar(64) NOT NULL,
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `picture` varchar(128) DEFAULT NULL COMMENT '封面',
  `content` varchar(512) DEFAULT NULL COMMENT '内容',
  `ad_space_id` varchar(64) DEFAULT NULL COMMENT '广告位id',
  `content_type` varchar(64) DEFAULT NULL COMMENT '内容类型',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '终止时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='广告内容';

/*Data for the table `ad_content` */

insert  into `ad_content`(`id`,`title`,`picture`,`content`,`ad_space_id`,`content_type`,`start_time`,`end_time`,`status`,`crt_time`,`upt_time`,`sys_project_id`) values ('1438403628292374528','测试1','http://106.12.134.162:28088/oss/upload/9ded536f99f06ffae5b44ffaf381f6f3.png','aa','1438402983502020608','url','2021-09-16 15:23:09','2021-09-16 15:23:11','normal','2021-09-16 15:25:23','2021-09-26 21:01:53','1402899832028266496'),('1448934668979142656','测试2','http://106.12.134.162:28088/oss/upload/f7d7a83191982c36fa631bf06484bef0.png','a','1438402983502020608','url','2021-10-13 00:00:00','2021-10-29 00:00:00','normal','2021-10-15 16:51:59','2021-10-15 16:51:59','1402899832028266496'),('1448934748498952192','测试3','http://106.12.134.162:28088/oss/upload/bb2d47bcba511155ad13249ac3716779.png','a','1438402983502020608','url','2021-10-13 00:00:00','2021-10-23 00:00:00','normal','2021-10-15 16:52:18','2021-10-15 16:52:18','1402899832028266496'),('1454798423717838848','Offline creation','https://js.chinahsdy.com/oss/upload/e296b7055d2eb73f49cda00abe8e762f.png','It is recommended to open flight mode to create wallet','1454792710358564864','url','2021-10-31 21:12:19','2021-10-31 21:12:17','normal','2021-10-31 21:12:27','2021-10-31 21:12:27','1454792442254458880'),('1455492035510272000','Mixed currency service','https://js.chinahsdy.com/oss/upload/e4e6fa561f2cf5dfab18e89250d02d8c.png','Keep the transaction anonymous and safe','1454792710358564864','url','2021-11-02 19:08:33','2021-11-02 19:08:35','normal','2021-11-02 19:08:37','2021-11-02 19:08:37','1454792442254458880'),('1455495324612165632','Protect privacy','https://js.chinahsdy.com/oss/upload/098d58bd7865efb3d6b39d32f21d7a38.png','Multi dimensional protection of user privacy and data security','1454792710358564864','url','2021-11-02 19:21:01','2022-01-06 00:00:00','normal','2021-11-02 19:21:41','2021-11-02 19:21:41','1454792442254458880'),('1455563644128460800','Offline creation','https://js.chinahsdy.com/oss/upload/e296b7055d2eb73f49cda00abe8e762f.png','It is recommended to open flight mode to create wallet','1455563547906932736','url','2021-11-02 23:52:59','2021-11-02 23:53:00','normal','2021-11-02 23:53:10','2021-11-02 23:53:10','1455563370651451392'),('1455563721169436672','Mixed currency service','https://js.chinahsdy.com/oss/upload/e4e6fa561f2cf5dfab18e89250d02d8c.png','Keep the transaction anonymous and safe','1455563547906932736','url','2021-11-02 23:53:22','2021-11-02 23:53:24','normal','2021-11-02 23:53:28','2021-11-02 23:53:28','1455563370651451392'),('1455563910647119872','Protect privacy','https://js.chinahsdy.com/oss/upload/098d58bd7865efb3d6b39d32f21d7a38.png','Multi dimensional protection of user privacy and data security','1455563547906932736','url','2021-11-02 23:54:08','2021-11-02 23:54:09','normal','2021-11-02 23:54:14','2021-11-02 23:54:14','1455563370651451392');

/*Table structure for table `ad_space` */

DROP TABLE IF EXISTS `ad_space`;

CREATE TABLE `ad_space` (
  `id` varchar(64) NOT NULL,
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `content` varchar(128) DEFAULT NULL COMMENT '说明',
  `space_type` varchar(64) DEFAULT NULL COMMENT '广告位类型',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='广告位';

/*Data for the table `ad_space` */

insert  into `ad_space`(`id`,`code`,`title`,`content`,`space_type`,`crt_time`,`upt_time`,`status`,`sys_project_id`) values ('1438402983502020608','home_banner','首页轮播','首页轮播',NULL,'2021-09-16 15:22:50','2021-09-26 21:02:44','normal','1402899832028266496'),('1454792710358564864','home_banner','首页轮播','a',NULL,'2021-10-31 20:49:45','2021-10-31 20:49:45','normal','1454792442254458880'),('1455563547906932736','home_banner','首页','1',NULL,'2021-11-02 23:52:47','2021-11-02 23:52:47','normal','1455563370651451392');

/*Table structure for table `i18_lang` */

DROP TABLE IF EXISTS `i18_lang`;

CREATE TABLE `i18_lang` (
  `id` varchar(64) NOT NULL,
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目语言';

/*Data for the table `i18_lang` */

insert  into `i18_lang`(`id`,`sys_project_id`,`title`,`code`,`crt_time`,`upt_time`) values ('1434422661445844992','1402899832028266496','中文(简体)','zh-CN','2021-09-05 15:46:27','2021-09-05 15:50:00'),('1434423949550157824','1402899832028266496','英文','en','2021-09-05 15:51:34','2021-09-05 15:51:34'),('1434424124620406784','1402899832028266496','中文(繁体)','zh-TW','2021-09-05 15:52:16','2021-09-05 15:52:16'),('1434424214579838976','1402899832028266496','日文','ja','2021-09-05 15:52:37','2021-09-05 15:52:37'),('1434424574711169024','1402899832028266496','韩文','ko','2021-09-05 15:54:03','2021-09-05 15:54:03'),('1436665431002710016','1436664451926327296','英文','en','2021-09-11 20:18:25','2021-09-11 20:18:25'),('1436665497692143616','1436664451926327296','中文','zh-CN','2021-09-11 20:18:41','2021-09-11 20:18:41'),('1458784189229826048','1455563370651451392','a','a','2021-11-11 21:10:28','2021-11-11 21:10:28'),('1458788431860596736','1455563370651451392','英语','11','2021-11-11 21:27:19','2021-11-11 21:27:19'),('1458836859974057984','1455563370651451392','影音','c','2021-11-12 00:39:45','2021-11-12 00:39:45');

/*Table structure for table `i18_resource` */

DROP TABLE IF EXISTS `i18_resource`;

CREATE TABLE `i18_resource` (
  `id` varchar(64) NOT NULL,
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  `content` varchar(256) DEFAULT NULL COMMENT '说明',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `resource_type` varchar(64) DEFAULT NULL COMMENT '资源类型',
  `data` varchar(2056) DEFAULT '{}' COMMENT '数据体',
  `page` varchar(64) DEFAULT NULL COMMENT '页面',
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `sys_project_id` (`sys_project_id`,`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源';

/*Data for the table `i18_resource` */

insert  into `i18_resource`(`id`,`sys_project_id`,`content`,`crt_time`,`upt_time`,`resource_type`,`data`,`page`,`code`) values ('1460214580285276160','1455563370651451392','无','2021-11-15 19:54:20','2021-11-15 19:54:20','text','{\"11\":\"a\",\"a\":\"a\",\"c\":\"a\"}','首页','sd'),('1460214580297859072','1455563370651451392','无','2021-11-15 19:54:20','2021-11-15 19:54:20','text','{\"11\":\"WEQ\",\"a\":\"\",\"c\":\"\"}','','ada');

/*Table structure for table `i18_template` */

DROP TABLE IF EXISTS `i18_template`;

CREATE TABLE `i18_template` (
  `id` varchar(64) NOT NULL,
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目',
  `group_name` varchar(64) DEFAULT NULL COMMENT '分组名',
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `content` varchar(128) DEFAULT NULL COMMENT '内容',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `resource_type` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='国际化模版';

/*Data for the table `i18_template` */

insert  into `i18_template`(`id`,`sys_project_id`,`group_name`,`code`,`title`,`content`,`crt_time`,`upt_time`,`resource_type`) values ('1434467546764935168','1402899832028266496','首页','logout','注销','注销','2021-09-05 18:44:48','2021-09-05 18:44:48',NULL),('1456231396891688960','1455563370651451392','首页','钱包','钱包','钱包','2021-11-04 20:06:35','2021-11-04 20:06:35','text'),('1458781574660096000','1455563370651451392','首页','111','111','接收','2021-11-11 21:00:04','2021-11-11 21:00:04','text');

/*Table structure for table `sys_client_version` */

DROP TABLE IF EXISTS `sys_client_version`;

CREATE TABLE `sys_client_version` (
  `id` varchar(64) NOT NULL,
  `sys_project_id` varchar(64) DEFAULT NULL,
  `title` varchar(64) DEFAULT NULL,
  `content` varchar(256) DEFAULT NULL,
  `version` double DEFAULT '0',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `coerce_update` tinyint(1) DEFAULT '0',
  `data` varchar(128) DEFAULT NULL,
  `update_type` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户端版本';

/*Data for the table `sys_client_version` */

insert  into `sys_client_version`(`id`,`sys_project_id`,`title`,`content`,`version`,`crt_time`,`upt_time`,`coerce_update`,`data`,`update_type`) values ('1458731682273165312',NULL,'ss','aa',0,'2021-11-11 17:41:49','2021-11-11 17:41:49',NULL,'daasda','publish_page'),('1458732971761598464',NULL,'da','da',1,'2021-11-11 17:46:57','2021-11-11 17:46:57',NULL,'daaa','publish_page'),('1458733322195697664',NULL,'eq','qe',0,'2021-11-11 17:48:20','2021-11-11 17:48:20',NULL,'eqq','publish_page'),('1458736579991306240','1458731052641026048','安卓1.0','安卓',1,'2021-11-11 18:01:17','2021-11-11 18:01:17',0,'https://aaa.hhw59.com/app-release1108.apk','publish_page');

/*Table structure for table `sys_dept` */

DROP TABLE IF EXISTS `sys_dept`;

CREATE TABLE `sys_dept` (
  `id` varchar(64) NOT NULL COMMENT 'id',
  `logo` varchar(64) DEFAULT NULL COMMENT '图标',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父编号',
  `parent_path` varchar(526) DEFAULT NULL COMMENT '父路径',
  `dept_name` varchar(64) DEFAULT NULL COMMENT '部门名称',
  `sort_value` int(11) DEFAULT NULL COMMENT '排序数字',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `children_size` int(11) DEFAULT '0' COMMENT '孩子数量',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统部门';

/*Data for the table `sys_dept` */

insert  into `sys_dept`(`id`,`logo`,`parent_id`,`parent_path`,`dept_name`,`sort_value`,`crt_time`,`upt_time`,`children_size`) values ('0','/upload/png/89cdd0dd797099da88ff68537e938983.png',NULL,NULL,'啦啦',0,'2020-12-10 00:00:00','2020-12-18 06:58:32',0),('2',NULL,NULL,NULL,NULL,NULL,NULL,NULL,0);

/*Table structure for table `sys_element` */

DROP TABLE IF EXISTS `sys_element`;

CREATE TABLE `sys_element` (
  `id` varchar(64) NOT NULL,
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `sys_menu_id` varchar(64) DEFAULT NULL COMMENT '菜单ud',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `element_type` varchar(32) DEFAULT NULL COMMENT '元素类型',
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统元素';

/*Data for the table `sys_element` */

insert  into `sys_element`(`id`,`title`,`code`,`sys_menu_id`,`crt_time`,`upt_time`,`element_type`,`sys_project_id`) values ('1432688175377022976','昵称','SysUser.username','1414874200413306880','2021-08-31 20:54:13','2021-08-31 21:27:13','field',NULL),('1432688221526949888','密码','SysUser.password','1414874200413306880','2021-08-31 20:54:24','2021-08-31 21:27:16','field',NULL),('1432696544519258112','昵称','SysUser.nickname','1414874200413306880','2021-08-31 21:27:29','2021-08-31 21:27:29','field',NULL);

/*Table structure for table `sys_element_rel` */

DROP TABLE IF EXISTS `sys_element_rel`;

CREATE TABLE `sys_element_rel` (
  `id` varchar(64) NOT NULL,
  `sys_user_id` varchar(64) DEFAULT NULL COMMENT '用户id',
  `sys_element_id` varchar(64) DEFAULT NULL COMMENT '元素id',
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `open` tinyint(1) DEFAULT NULL COMMENT '是否开启',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户元素关联';

/*Data for the table `sys_element_rel` */

insert  into `sys_element_rel`(`id`,`sys_user_id`,`sys_element_id`,`code`,`open`,`crt_time`,`upt_time`) values ('1449262986450436096','1','1432688175377022976',NULL,1,'2021-10-16 14:36:36','2021-10-16 14:36:36'),('1449262986458824704','1','1432688221526949888',NULL,1,'2021-10-16 14:36:36','2021-10-16 14:36:36'),('1449262986467213312','1','1432696544519258112',NULL,1,'2021-10-16 14:36:36','2021-10-16 14:36:36');

/*Table structure for table `sys_enum` */

DROP TABLE IF EXISTS `sys_enum`;

CREATE TABLE `sys_enum` (
  `id` varchar(64) NOT NULL,
  `code` varchar(64) DEFAULT NULL COMMENT '编码',
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `value` varchar(128) DEFAULT NULL COMMENT '值',
  `enum_type` varchar(64) DEFAULT NULL COMMENT '枚举类型',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父id',
  `value_type` varchar(64) DEFAULT NULL COMMENT '值类型',
  `parent_path` varchar(128) DEFAULT NULL COMMENT '父路径',
  `children_size` int(11) DEFAULT '0' COMMENT '孩子数量',
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='参数枚举';

/*Data for the table `sys_enum` */

/*Table structure for table `sys_menu` */

DROP TABLE IF EXISTS `sys_menu`;

CREATE TABLE `sys_menu` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `menu_name` varchar(64) DEFAULT NULL COMMENT '名称',
  `parent_id` varchar(64) DEFAULT NULL COMMENT '父编号',
  `parent_path` varchar(128) DEFAULT NULL,
  `sort_value` int(11) DEFAULT '0' COMMENT '排序值',
  `path` varchar(64) DEFAULT NULL COMMENT '路径',
  `menu_type` varchar(32) DEFAULT NULL COMMENT '类型',
  `code` varchar(64) DEFAULT NULL COMMENT '权限编码',
  `icon` varchar(64) DEFAULT NULL COMMENT '图标',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `hidden` tinyint(1) DEFAULT '0' COMMENT '是否隐藏',
  `children_size` int(11) DEFAULT '0' COMMENT '孩子数量',
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  `props` varchar(256) DEFAULT NULL COMMENT '参数',
  `component` varchar(256) DEFAULT NULL COMMENT '组件路径',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统菜单';

/*Data for the table `sys_menu` */

/*Table structure for table `sys_project` */

DROP TABLE IF EXISTS `sys_project`;

CREATE TABLE `sys_project` (
  `id` varchar(64) NOT NULL,
  `title` varchar(64) DEFAULT NULL COMMENT '标题',
  `app_id` varchar(64) DEFAULT NULL COMMENT '应用id',
  `crt_time` datetime DEFAULT NULL,
  `upt_time` datetime DEFAULT NULL,
  `content` varchar(128) DEFAULT NULL COMMENT '说明',
  `project_type` varchar(64) DEFAULT NULL COMMENT '项目类型',
  `domain` varchar(64) DEFAULT NULL COMMENT '域名',
  `status` varchar(32) DEFAULT NULL COMMENT '状态',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目';

/*Data for the table `sys_project` */

insert  into `sys_project`(`id`,`title`,`app_id`,`crt_time`,`upt_time`,`content`,`project_type`,`domain`,`status`) values ('1450080038912262144','授权系统','admin','2021-10-18 20:43:17','2021-10-18 20:43:17','12','admin','https://maskanalyse.com/vue/admin/index.html#/',NULL),('1450386346660069376','数据分析后台','statistics-admin','2021-10-19 17:00:26','2021-10-19 17:00:26',NULL,'admin','https://maskanalyse.com/vue/statistics/index.html#/',NULL),('1451073457457463296','应用平台','app-platform-h5','2021-10-21 14:30:46','2021-10-21 14:30:46','应用平台','pc','http://appahee.com/','normal'),('1453731291445854208','New_kkm','New_kkm','2021-10-28 22:32:03','2021-10-28 22:32:03',NULL,'pc','New_kkm',NULL),('1455563370651451392','钱包后台','wallet_admin','2021-11-02 23:52:05','2021-11-02 23:52:05',NULL,'admin','https://maskanalyse.com/vue/wallet/index.html#/',NULL),('1458730171023491072','钱包ios','masktoken-ios','2021-11-11 17:35:49','2021-11-11 17:35:49',NULL,'ios',NULL,NULL),('1458731052641026048','masktoken安卓','masktoken-android','2021-11-11 17:39:19','2021-11-11 17:39:19',NULL,'android',NULL,'normal');

/*Table structure for table `sys_role` */

DROP TABLE IF EXISTS `sys_role`;

CREATE TABLE `sys_role` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `role_name` varchar(64) DEFAULT NULL COMMENT '角色名称',
  `sort_value` int(11) DEFAULT NULL COMMENT '排序数值',
  `data` text COMMENT '属性数据',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `code` varchar(64) DEFAULT NULL,
  `sys_element_ids` varchar(2056) DEFAULT NULL COMMENT '元素集合',
  `sys_view_ids` varchar(1028) DEFAULT NULL COMMENT '视图集合',
  `sys_project_id` varchar(64) DEFAULT NULL COMMENT '项目id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统角色';

/*Data for the table `sys_role` */

insert  into `sys_role`(`id`,`role_name`,`sort_value`,`data`,`crt_time`,`upt_time`,`code`,`sys_element_ids`,`sys_view_ids`,`sys_project_id`) values ('1432688344227119104','admin',NULL,'[[\"1450444488446050304\",\"1450434644825604096\"],[\"1450444488446050304\",\"1450445986416558080\"]]','2021-08-31 20:54:53','2021-09-15 16:18:27',NULL,NULL,NULL,'1450080038912262144'),('1450386411759861760','数据分析-管理员',NULL,NULL,'2021-10-19 17:00:42','2021-10-19 17:00:42',NULL,NULL,NULL,'1450386346660069376'),('1455573665545453568','钱包管理',NULL,NULL,'2021-11-03 00:32:59','2021-11-03 00:32:59',NULL,NULL,NULL,'1455563370651451392');

/*Table structure for table `sys_user` */

DROP TABLE IF EXISTS `sys_user`;

CREATE TABLE `sys_user` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user` */

insert  into `sys_user`(`id`,`username`,`nickname`,`email`,`phone_number`,`sex`,`avatar`,`password`,`salt`,`login_ip`,`login_date`,`sys_dept_id`,`sys_role_ids`,`crt_time`,`upt_time`,`status`,`data`,`login_time`) values ('1','admin','管理员','管理员','管理员','man','https://js.chinahsdy.com/oss/upload/b8a7bdf01422d13667ecfd82c6b7f663.hdslb','123456',NULL,'127.0.0.1','2021-05-07 00:00:00','0','1432688344227119104,1450386411759861760','2020-12-15 00:00:00','2021-11-10 11:57:08','normal',NULL,'2021-05-31 00:00:00'),('1407735480304271360','dev','dev','1','1','man','https://js.chinahsdy.com/oss/upload/a3bfbb7399b891a3b1ee0f0ac68bb059.jsp','dev',NULL,NULL,NULL,'0','1407293225915383808','2021-06-24 00:00:00','2021-07-26 22:10:47','freeze',NULL,'2021-07-15 00:00:00'),('1455574906715504640','super_admin','super_admin','super_admin','super_admin','man','https://js.chinahsdy.com/oss/upload/17f8a46a50de0d67f86b4d936708a7db.jfif','super_admin',NULL,NULL,NULL,'0','1455573665545453568,1450386411759861760,1432688344227119104','2021-11-03 00:37:55','2021-11-03 00:37:55',NULL,NULL,NULL),('1455575729780555776','wallet','wallet','wallet','wallet','man','https://js.chinahsdy.com/oss/upload/17f8a46a50de0d67f86b4d936708a7db.jfif','wallet',NULL,NULL,NULL,'0','1455573665545453568,1432688344227119104','2021-11-03 00:41:11','2021-11-03 00:41:11',NULL,NULL,NULL);

/*Table structure for table `sys_user_0` */

DROP TABLE IF EXISTS `sys_user_0`;

CREATE TABLE `sys_user_0` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user_0` */

/*Table structure for table `sys_user_1` */

DROP TABLE IF EXISTS `sys_user_1`;

CREATE TABLE `sys_user_1` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user_1` */

/*Table structure for table `sys_user_2` */

DROP TABLE IF EXISTS `sys_user_2`;

CREATE TABLE `sys_user_2` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user_2` */

/*Table structure for table `sys_user_3` */

DROP TABLE IF EXISTS `sys_user_3`;

CREATE TABLE `sys_user_3` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user_3` */

/*Table structure for table `sys_user_4` */

DROP TABLE IF EXISTS `sys_user_4`;

CREATE TABLE `sys_user_4` (
  `id` varchar(64) NOT NULL COMMENT '编号',
  `username` varchar(64) DEFAULT NULL COMMENT '账号',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `email` varchar(64) DEFAULT NULL COMMENT '邮箱',
  `phone_number` varchar(16) DEFAULT NULL COMMENT '手机',
  `sex` varchar(8) DEFAULT NULL COMMENT '性别',
  `avatar` varchar(128) DEFAULT NULL COMMENT '头像',
  `password` varchar(64) DEFAULT NULL COMMENT '密码',
  `salt` varchar(64) DEFAULT NULL COMMENT '盐',
  `login_ip` varchar(64) DEFAULT NULL COMMENT '登录ip',
  `login_date` datetime DEFAULT NULL COMMENT '登录时间',
  `sys_dept_id` varchar(64) DEFAULT NULL COMMENT '部门id',
  `sys_role_ids` varchar(256) DEFAULT NULL COMMENT '角色集合',
  `crt_time` datetime DEFAULT NULL COMMENT '创建时间',
  `upt_time` datetime DEFAULT NULL COMMENT '修改时间',
  `status` varchar(64) DEFAULT NULL COMMENT '状态',
  `data` varchar(1024) DEFAULT NULL COMMENT '菜单权限id集合',
  `login_time` datetime DEFAULT NULL COMMENT '登录时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='系统用户';

/*Data for the table `sys_user_4` */

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;
