update routing_ccr
set
  operation_no = :operation_no,
  total_touch_time = :total_touch_time,
  pre_ccr_buffer = :pre_ccr_buffer,
  post_ccr_buffer = :post_ccr_buffer,
  best_end_date = :best_end_date,
  rowversion = sysdate
where contract = :contract
  and part_no = :part_no
  and bom_type_db = :bom_type_db
  and routing_revision_no = :routing_revision_no
  and routing_alternative_no = :routing_alternative_no
