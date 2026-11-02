classDiagram
direction BT
class node7 {
   bigint actor_id
   varchar(100) action
   varchar(50) resource_type
   bigint resource_id
   jsonb detail
   inet ip_address
   text user_agent
   timestamp with time zone created_at
   bigint id
}
class node4 {
   bigint order_id
   bigint sitter_id
   bigint author_id
   text content
   text[] photos
   text[] tags
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class notifications {
   bigint user_id
   notification_type type
   varchar(128) title
   text body
   jsonb extra
   boolean is_read
   timestamp with time zone created_at
   bigint id
}
class order_pet {
   bigint order_id
   bigint pet_id
   date stay_date
   time checkin_time
   time checkout_time
   varchar(20) status
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class node9 {
   varchar(64) order_no
   bigint owner_id
   bigint sitter_id
   order_status status
   date start_date
   date end_date
   integer total_days
   smallint pet_count
   numeric(12,2) estimated_amount
   numeric(12,2) total_amount
   payment_status payment_status
   varchar(255) cancel_reason
   timestamp with time zone cancel_time
   timestamp with time zone paid_at
   timestamp with time zone accepted_at
   timestamp with time zone completed_at
   timestamp with time zone evaluated_at
   text remark
   timestamp with time zone created_at
   timestamp with time zone updated_at
   timestamp with time zone deleted_at
   bigint id
}
class payments {
   bigint order_id
   numeric(12,2) amount
   pay_method method
   varchar(128) transaction_id
   varchar(20) status
   jsonb raw_response
   timestamp with time zone created_at
   timestamp with time zone paid_at
   timestamp with time zone refunded_at
   bigint id
}
class node2 {
   bigint owner_id
   varchar(64) name
   pet_species species
   varchar(100) breed
   pet_size body_size
   integer age_month
   numeric(6,2) weight_kg
   boolean vaccinated
   boolean sterilized
   text[] photo_urls
   text avatar_url
   text notes
   timestamp with time zone created_at
   timestamp with time zone updated_at
   timestamp with time zone deleted_at
   bigint id
}
class refresh_tokens {
   bigint user_id
   varchar(128) token_hash
   timestamp with time zone expires_at
   timestamp with time zone revoked_at
   timestamp with time zone created_at
   bigint id
}
class node1 {
   bigint order_id
   bigint sitter_id
   bigint owner_id
   smallint overall_score
   smallint service_score
   smallint environment_score
   smallint hygiene_score
   text owner_comment
   text[] owner_photos
   text reply_text
   timestamp with time zone reply_at
   boolean is_visible
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class node5 {
   bigint sitter_id
   date cap_date
   integer used
   integer capacity
   timestamp with time zone created_at
   timestamp with time zone updated_at
   bigint id
}
class node0 {
   bigint user_id
   varchar(128) name
   sitter_type type
   text description
   numeric(10,2) daily_price
   numeric(10,2) visit_price
   price_mode price_mode
   integer capacity
   varchar(255) address
   numeric(10,7) latitude
   numeric(10,7) longitude
   numeric(3,2) avg_score  /* Denormalized average, updated by trigger on review insert */
   integer review_count
   text cover_photo_url
   text[] detail_photos
   sitter_status status
   timestamp with time zone banned_at
   varchar(255) banned_reason
   timestamp with time zone created_at
   timestamp with time zone updated_at
   timestamp with time zone deleted_at
   bigint id
}
class node10 {
   varchar(50) username
   varchar(256) password_hash
   varchar(64) nickname
   varchar(20) phone
   varchar(128) email
   user_role role
   text avatar_url
   smallint status  /* 1=active, 0=disabled */
   smallint login_fail_count
   timestamp with time zone locked_until  /* Account lock expiry after repeated login failures */
   timestamp with time zone created_at
   timestamp with time zone updated_at
   timestamp with time zone deleted_at
   bigint id
}

node7  -->  node10 : actor_id:id
node4  -->  node9 : order_id:id
node4  -->  node0 : sitter_id:id
node4  -->  node10 : author_id:id
notifications  -->  node10 : user_id:id
order_pet  -->  node9 : order_id:id
order_pet  -->  node2 : pet_id:id
node9  -->  node0 : sitter_id:id
node9  -->  node10 : owner_id:id
payments  -->  node9 : order_id:id
node2  -->  node10 : owner_id:id
refresh_tokens  -->  node10 : user_id:id
node1  -->  node9 : order_id:id
node1  -->  node0 : sitter_id:id
node1  -->  node10 : owner_id:id
node5  -->  node0 : sitter_id:id
node0  -->  node10 : user_id:id
