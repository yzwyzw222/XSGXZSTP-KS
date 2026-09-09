-- =====================================================================
-- 学术关系知识图谱构建平台 — 知网真实数据导入（姚期智 14 篇论文）
-- 数据来源：知网导出文件 CNKI-20260907152836006.xls（HTML 表格格式，本脚本由其解析生成）
-- 说明：真实文献（2006-2023），与样例数据（id 100-130，虚构演示数据）相互独立，
--       所有导入实体使用 id 200-299，避免与样例数据、手工录入数据冲突。
-- 用法：mysql --default-character-set=utf8mb4 -uroot -p academic_graph < docs/sql/cnki-import.sql
-- 可重复执行：脚本先删除 200-299 的导入数据再插入。
-- 注意：重跑 sample-data.sql 会清掉全部 id>=100 的数据（含本导入），之后需重跑本脚本。
-- =====================================================================

SET NAMES utf8mb4;

-- 先清理旧导入数据（按外键依赖逆序删除）
DELETE FROM paper_reference WHERE citing_paper_id BETWEEN 200 AND 299 OR cited_paper_id BETWEEN 200 AND 299;
DELETE FROM paper_keyword    WHERE paper_id BETWEEN 200 AND 299;
DELETE FROM paper_author     WHERE paper_id BETWEEN 200 AND 299;
DELETE FROM paper            WHERE id BETWEEN 200 AND 299;
DELETE FROM paper_keyword    WHERE keyword_id BETWEEN 200 AND 299;
DELETE FROM keyword          WHERE id BETWEEN 200 AND 299;
DELETE FROM author           WHERE id BETWEEN 200 AND 299;
DELETE FROM institution      WHERE id BETWEEN 200 AND 299;
DELETE FROM venue            WHERE id BETWEEN 200 AND 299;

-- 1. 机构（8 所：论文署名单位）
INSERT INTO institution (id, display_name, country_code, institution_type) VALUES
    (201, '中国科学院', 'CN', 'institute'),
    (202, '清华大学', 'CN', 'university'),
    (203, '清华大学交叉信息研究院', 'CN', 'institute'),
    (204, '美国科学院', 'US', 'institute'),
    (205, '清华大学高等研究中心', 'CN', 'institute'),
    (206, '微软', 'US', 'company'),
    (207, '微软亚洲研究院', 'CN', 'institute'),
    (208, '麻省理工学院', 'US', 'university');

-- 2. 作者（11 位，姚期智及其合作者）
INSERT INTO author (id, display_name, orcid) VALUES
    (201, '姚期智', NULL),
    (202, '赵广立', NULL),
    (203, '邓晖', NULL),
    (204, '顾秉林', NULL),
    (205, '王大中', NULL),
    (206, '汪劲松', NULL),
    (207, '陈皓明', NULL),
    (208, '本刊记者', NULL),
    (209, 'Rick Rashid', NULL),
    (210, '沈向洋', NULL),
    (211, 'Eric Grimson', NULL);

-- 3. 发表渠道（11 个：期刊/报纸/会议论文集）
INSERT INTO venue (id, display_name, issn, venue_type) VALUES
    (201, '新经济导刊', '1009-959X', 'journal'),
    (202, '中国科学报', NULL, 'newspaper'),
    (203, '国际人才交流', '1001-0114', 'journal'),
    (204, '光明日报', NULL, 'newspaper'),
    (205, '中国高等教育', '1002-4417', 'journal'),
    (206, '中国高教研究', '1004-3667', 'journal'),
    (207, '计算机教育', '1672-5913', 'journal'),
    (208, '质量提升与建设高等教育强国——2011年高等教育国际论坛论文集', NULL, 'conference'),
    (209, '清华大学教育研究', '1001-4519', 'journal'),
    (210, '程序员', '1672-3252', 'journal'),
    (211, '软件世界', '1005-2348', 'journal');

-- 4. 关键词（13 个，field_name 按中图分类号归入"教育学"）
INSERT INTO keyword (id, name, field_name) VALUES
    (201, '学科融合', '教育学'),
    (202, '世界一流大学', '教育学'),
    (203, '本科教育', '教育学'),
    (204, '国内一流大学', '教育学'),
    (205, '普林斯顿大学', '教育学'),
    (206, '图灵奖', '教育学'),
    (207, '拔尖创新人才培养', '教育学'),
    (208, '清华大学', '教育学'),
    (209, '新探索', '教育学'),
    (210, '实验班', '教育学'),
    (211, '创新性实践教育', '教育学'),
    (212, '人才培养', '教育学'),
    (213, '学科建设', '教育学');

-- 5. 论文（14 篇；publication_year 是生成列由 publication_date 自动算出，无需插入）
INSERT INTO paper (id, title, doi, paper_type, language, publication_date, abstract_text, citation_count, venue_id, volume, period, page_count, clc_number, url) VALUES
    (201, '迈向具身通用人工智能', NULL, 'JOURNAL_ARTICLE', 'zh', '2023-08-20', '最近出现的ChatGPT是人工智能发展在学术上的一个突破，同时它也在各行各业中创造了许多新的价值，未来人工智能的发展方向是我们需要思考的问题。众所周知，ChatGPT是人工智能技术驱动的自然语言处理工具，具有强大的自然语言处理能力，被视为“缸中之脑”。（“缸中之脑”是希拉里·普特南1981年在他的《理性，真理与历史》一书中提出的假想。）然而，要想让通用人工智能充分发挥出力量，未来的AGI（通用人工智能）需要有具身的实体，让它能够和真实的物理世界相交互来完成各项任务，这样才能创造出更大的价值。', 0, 201, NULL, '8', '20-22', 'TP18', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPH-PmV5nxS81AppxEvR4pLIzb3tOVhbzqaWb4qsex3c7O0blGAdPp09aFfQXqqVL9REOpq2E3YtRNMk84OSF2jvnkWMaqiA_cyWJ_HZ1JWUhdN99Ul62_MuKxTvd6SKDhsaSrfcVXzfp71BMoGiSAT1hXwbKKz3L4g=&uniplatform=NZKPT&language=CHS'),
    (202, '学科融合“令人兴奋”', '10.28514/n.cnki.nkxsb.2020.003551', 'OTHER', 'zh', '2020-11-20', '我分享一点关于科学创新的想法。 首先，谈一谈学科融合。近年来我们谈到很多学科交叉，几乎每隔几年就会有一个新领域产生。其实，这背后有一个更深层的趋势，那就是不同的学科正在慢慢融合。 很少人谈到学科融合。关于学科融合，我举两个简单的例子。 一个是，近年', 0, 202, NULL, NULL, '1', 'G423', 'https://link.cnki.net/doi/10.28514/n.cnki.nkxsb.2020.003551'),
    (203, '归国办“软件科学实验班”', NULL, 'JOURNAL_ARTICLE', 'zh', '2018-08-06', '2003年8月起,姚期智被清华大学聘为讲席教授,担任清华大学计算机科学与技术系讲席教授组(理论计算机科学)首席教授。在此后的一年时间里,他多次来到清华大学,举办学术讲座,指导研究生,并且为中国和清华大学的计算机科学与技术的发展献计献策。在中国现代化建设迅速发展和"科教兴国"战略的感召下,2004年6月,姚期智决定辞掉美国普林斯顿大学终身教职,卖掉美国的房子,回到中国定居和工作。2004年9月起,姚期智正式受聘为清华大', 0, 203, NULL, '8', '28', 'K826.16', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPEUsfFWgJHKUmRH17KOT6q8haxrEO58AO6JrnHv8f7V93pYGO0cXhjNlp3MsgaGr5xj4iTYKOpf4boGJo03Ut-PK0lR97VV0bzRmBZixMENgd9zfH6-epemnskxCTVRn5iAShNUONYagpPkPxdDG6ddEUkNmrLhpzw=&uniplatform=NZKPT&language=CHS'),
    (204, '好教育必须挑战学生', NULL, 'OTHER', 'zh', '2017-01-10', '开栏的话 习近平总书记在全国高校思想政治工作会议上强调，我国有独特的历史、独特的文化、独特的国情，决定了我国必须走自己的高等教育发展道路，扎实办好中国特色社会主义高校。 这既关乎办学，更关乎人才培养，与高校培养什么样的人、如何培养人以及为谁培养人', 0, 204, NULL, NULL, '3', 'G649.2', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPH25Zmjh5jC-LC_MkrmFvGV_yfcFhr1khcyUeIL8HZkxnkXYO9kgSmBQQMINxQLjfxuVaPkK6JcHAZKdJeSUwmGRQuhiuy73gBwMQ9EJNoTxYPuxXLqdNrIbr-z_DdDn44OvbvDGCgVG6Yw4Wurk5kYuYKytJQwxeKb24rO7QflcA==&uniplatform=NZKPT&language=CHS'),
    (205, '创新型学术人才培养的改革与实践', NULL, 'JOURNAL_ARTICLE', 'zh', '2013-08-18', '习近平总书记多次在不同场合强调科技创新人才培养。科教兴国的核心在于人才战略,人才战略的核心则在于创新型学术人才培养。就计算机学科而言,我国本科教育水平与世界顶尖大学还有相当的差距,主要表现在:课程的前沿性与深度不足,缺乏培养创新型学术人才的良好学术环境和氛围,毕业生的工程能力强而深度学术思维能力弱。在中国应该如何培养创新型学术人才?我们认为,精英教育要从本科开始。本科阶段是世界观、方法论、价值观和思维方式成型的重要时期,对于学术人才而言将决定其', 0, 205, NULL, 'Z3', '23-24', 'C964.2;G647', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPFBmKwOibLpJfTiYCClK-EZYNbQ6j5rmO4rdFgveR0mutEOa3u7KTqHvAbz-WPDWxSpqlb6G6xmn-PzO2J2vTcs1lQRO9_5K8XIYn3bPybhC1R2r2QavtreBdDw4WxE42D1MIUqjsrkUC3VqWRbuZRIrq5AWDdZO4g=&uniplatform=NZKPT&language=CHS'),
    (206, '拔尖创新人才培养的新理念与新探索', '10.16298/j.cnki.1004-3667.2011.12.019', 'JOURNAL_ARTICLE', 'zh', '2011-12-20', '为建设世界一流大学,清华大学制定了"三个九年,分三步走"的总体战略,经过若干年的努力,清华大学的办学实力、社会声誉和国际影响力不断提升。我非常有幸能够在2004年到清华大学工作,有机会参加创建世界一流大学的工作。清华大学有很多个培养拔尖创新人才的实验基地,我主要就计算机科学实', 0, 206, NULL, '12', '1-2', 'G642.0', 'https://link.cnki.net/doi/10.16298/j.cnki.1004-3667.2011.12.019'),
    (207, '拔尖创新人才培养新思路', '10.16512/j.cnki.jsjjy.2011.23.006', 'JOURNAL_ARTICLE', 'zh', '2011-12-10', '对世界和中国来说,人才培养是非常重要的使命。而对大学来说,这是一个最重要的任务,因为培养人才、培养高级人才是大学最重要的基本任务。胡锦涛总书记在清华大学建校一百周年庆祝大会上指示我们,全面提高高等教育质量,必须要大', 0, 207, NULL, '23', '1-3', 'G642.0', 'https://link.cnki.net/doi/10.16512/j.cnki.jsjjy.2011.23.006'),
    (208, '拔尖创新人才培养的新理念与新探索', NULL, 'CONFERENCE_PAPER', 'zh', '2011-10-23', '为建设世界一流大学,清华大学制定了"三个九年,分三步走"的总体战略,经过若干年的努力,清华大学的办学实力、社会声誉和国际影响力不断提升。我非常有幸能够在2004年到清华大学工作,有机会参加创建世界一流大学的工作。清华大学有很多个培养拔尖创新人才的实验基地,我主要就计算机科学实验班的探索实践谈一谈拔尖创新人才培养问题。一、大学教育最重要的目的就是让学生发现其所擅长的方向2011年4月,胡锦涛总书记在庆祝清华大学建校一百周年大会上发表重要讲话,对全面提高高等教育质量、加快世界一流大学建设步伐、推动我国高等教育事业改革发展提出明确要求;', 0, 208, NULL, NULL, '43-44', 'G649.2', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPE3JaKycijWm41VzxphcukHWlLulRI0r-MiHfa2jeK0_T2Jk6EEQNuEA6IxSL8LvmP7eiZfyc7GAwZ0hEip4wYQEW74YBNeCDi-rXiRfK3PkRx8u3WWbUv5NBez00l6hsJMCYcqL4hGSYLY1lmbD8u7oJiWyVJALqE=&uniplatform=NZKPT&language=CHS'),
    (209, '浅谈计算机科学人才培育', '10.16512/j.cnki.jsjjy.2011.11.008', 'JOURNAL_ARTICLE', 'zh', '2011-06-10', '对世界和中国来说,人才培养是非常重要的使命。而对大学来说,这是一个最重要的任务,因为培养人才、培养高级人才是大学最重要的基本任务。胡锦涛总书记在清华大学百年校庆的庆祝大会上特别', 0, 207, NULL, '11', '2-5', 'TP3-4', 'https://link.cnki.net/doi/10.16512/j.cnki.jsjjy.2011.11.008'),
    (210, '创新性实践教育——基于高水平学科建设的创新人才培养之路', '10.14138/j.1001-4519.2010.01.007', 'JOURNAL_ARTICLE', 'zh', '2010-02-10', '创新人才培养是新时期我国人才战略的核心任务,是清华大学人才培养的崇高使命。经过多年努力,清华大学探索和建立了一种创新人才培养的新模式,即以高水平学科建设为基础的创新性实践教育。', 0, 209, '31', '1', '1-5', 'G642.0', 'https://link.cnki.net/doi/10.14138/j.1001-4519.2010.01.007'),
    (211, '中国图灵之路', '10.14138/j.1001-4519.2009.06.006', 'JOURNAL_ARTICLE', 'zh', '2009-12-20', '姚期智先生的《中国图灵之路》以自己的经历和实践对中国建设世界一流的学科和大学作了探索和思考,这对中国大学发展和高等教育研究都极有价值。姚先生的演讲能近取譬,深入浅出,温文尔雅,让人如沐春风,味之无穷。本刊发表时尽量保持演讲原貌,使读者一睹大师的睿智和智者的风采。', 0, 209, '30', '6', '1-8', 'G649.2', 'https://link.cnki.net/doi/10.14138/j.1001-4519.2009.06.006'),
    (212, '中国的图灵之路', NULL, 'JOURNAL_ARTICLE', 'zh', '2008-08-01', '这是图灵奖得主姚期智先生最近在清华的一个演讲,里面谈到了创造性思维方式,这对我们大家都会有所启发。讲演者:姚期智地点:清华大学主楼接待厅时间:2008年7月7日过去的十年,有很多人讨论过,在中国从短到中期,有没有可能建立成一流的大学,和是否可以培养出一流的科学家,比如诺贝尔奖得主这样的科学家。因为我是学习计算机的,我将以上的问题转换为,是否在中国短期内有可能建立一流的计算机学科,和在中国有没有可能培养出图灵奖得主。', 0, 210, NULL, '8', '111-113+8', 'F426.671', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPHaB4--7B8t2aJBG52408dsW8m5pTTcO2hMGxTirB5S7zGrBRIxdKIa-KgIfvlTeRbJK7SGkAZWi9ctNDmp_SQ33J28139MrB0PvSAKv0Bz62idvZ1eZl9QVEKvfzqaXZGMPx5tSXuiN71Bdv3IcrcquK4GO5jjxCw=&uniplatform=NZKPT&language=CHS'),
    (213, '计算:追求信任与安全的梦想', NULL, 'JOURNAL_ARTICLE', 'zh', '2006-11-05', '什么才是可靠的信息,如何将可靠的信息在网络中安全的传送?无疑已经成为软件业界的一道现实的难题。', 0, 211, NULL, '21', '27', 'F426.672', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPEihg3S6ZHp5FZK488-2Blq6nDpvhwM3Jdn-tP2a2-mYFXE26sYAIkRAzVGVz79x168BmDGThgTDl2kgX434fKD576x4iFxkvTMwg1mpuiTdYtznyppsjNr-yVrdKxQQDH1ZXNe9JITEenD5E3Wsb7Q5ZlOb7_B_gQ=&uniplatform=NZKPT&language=CHS'),
    (214, '“质”取软件未来', NULL, 'JOURNAL_ARTICLE', 'zh', '2006-11-05', NULL, 0, 211, NULL, '21', '28', 'F426.672', 'https://kns.cnki.net/kcms2/article/abstract?v=HTCMX8yYpPHTU-2SrcKIB7P4-t5b_GrHimYb-Bad3TNNATiNegam8701aHjDTUYKpPIZ3UHcMlx0kugNq9mG-KnCVXYkuDzG-UeG70HM2K6aDBFoWKdkhdkbAx8mjihCFg-AOSEI5YhZNLEdwZmeG6vxa-dmJGsmxwbSCDMLBP4=&uniplatform=NZKPT&language=CHS');

-- 6. 论文-作者署名（author_position 按知网作者顺序，institution_id 与作者位次一一对应）
INSERT INTO paper_author (paper_id, author_id, author_position, institution_id) VALUES
    (201, 201, 1, 201),
    (202, 201, 1, 201),
    (202, 202, 2, NULL),
    (203, 201, 1, 201),
    (204, 203, 1, 202),
    (204, 201, 2, NULL),
    (205, 201, 1, 203),
    (206, 201, 1, 204),
    (207, 201, 1, 203),
    (208, 201, 1, 204),
    (209, 201, 1, 202),
    (210, 204, 1, 202),
    (210, 205, 2, NULL),
    (210, 206, 3, NULL),
    (210, 207, 4, NULL),
    (210, 201, 5, NULL),
    (211, 201, 1, 205),
    (212, 201, 1, NULL),
    (212, 208, 2, NULL),
    (213, 201, 1, 202),
    (214, 209, 1, 206),
    (214, 210, 2, 207),
    (214, 201, 3, 205),
    (214, 211, 4, 208);

-- 7. 论文-关键词
INSERT INTO paper_keyword (paper_id, keyword_id, keyword_position) VALUES
    (202, 201, 1),
    (204, 202, 1),
    (204, 203, 2),
    (204, 204, 3),
    (204, 205, 4),
    (204, 206, 5),
    (208, 207, 1),
    (208, 208, 2),
    (208, 209, 3),
    (208, 210, 4),
    (210, 211, 1),
    (210, 212, 2),
    (210, 213, 3);

-- 8. 图同步 Outbox：让 Neo4j 投影任务把导入数据同步进图（与样例脚本同一机制）
INSERT INTO graph_sync_event (entity_type, entity_id, event_type, status)
SELECT 'VENUE', id, 'UPSERT', 'PENDING' FROM venue WHERE id BETWEEN 200 AND 299;
INSERT INTO graph_sync_event (entity_type, entity_id, event_type, status)
SELECT 'INSTITUTION', id, 'UPSERT', 'PENDING' FROM institution WHERE id BETWEEN 200 AND 299;
INSERT INTO graph_sync_event (entity_type, entity_id, event_type, status)
SELECT 'KEYWORD', id, 'UPSERT', 'PENDING' FROM keyword WHERE id BETWEEN 200 AND 299;
INSERT INTO graph_sync_event (entity_type, entity_id, event_type, status)
SELECT 'AUTHOR', id, 'UPSERT', 'PENDING' FROM author WHERE id BETWEEN 200 AND 299;
INSERT INTO graph_sync_event (entity_type, entity_id, event_type, status)
SELECT 'PAPER', id, 'UPSERT', 'PENDING' FROM paper WHERE id BETWEEN 200 AND 299;

SELECT '知网数据导入完成' AS result,
       (SELECT COUNT(*) FROM paper WHERE id BETWEEN 200 AND 299)       AS papers,
       (SELECT COUNT(*) FROM author WHERE id BETWEEN 200 AND 299)      AS authors,
       (SELECT COUNT(*) FROM institution WHERE id BETWEEN 200 AND 299) AS institutions,
       (SELECT COUNT(*) FROM keyword WHERE id BETWEEN 200 AND 299)     AS keywords,
       (SELECT COUNT(*) FROM venue WHERE id BETWEEN 200 AND 299)       AS venues;
