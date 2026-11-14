USE wsw;
-- 1) 每对用户保留 id 最小的一条会话
CREATE TEMPORARY TABLE keep_conv AS
SELECT user_a, user_b, MIN(id) AS keep_id FROM conversation GROUP BY user_a, user_b;
-- 2) 把重复会话里的消息挪到保留的那条
UPDATE message m
JOIN conversation c ON m.conversation_id = c.id
JOIN keep_conv k ON k.user_a = c.user_a AND k.user_b = c.user_b
SET m.conversation_id = k.keep_id
WHERE c.id <> k.keep_id;
-- 3) 删掉多余的会话
DELETE c FROM conversation c
JOIN keep_conv k ON k.user_a = c.user_a AND k.user_b = c.user_b
WHERE c.id <> k.keep_id;
-- 4) 改成按人唯一
ALTER TABLE conversation
  DROP INDEX uk_task_pair,
  MODIFY COLUMN task_id BIGINT UNSIGNED DEFAULT NULL,
  ADD UNIQUE KEY uk_pair (user_a, user_b);
