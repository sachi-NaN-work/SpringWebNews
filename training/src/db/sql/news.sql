SELECT n.news_id AS id, n.category_id, nc.news_title AS title, n.published_at
FROM news n
JOIN news_content nc ON nc.news_id = n.news_id
