update routing_ccr
set
  last_operation_no = operation_no,
  last_work_center_no = work_center_no,
  last_total_touch_time = total_touch_time,
  last_pre_ccr_buffer = pre_ccr_buffer,
  last_post_ccr_buffer = post_ccr_buffer,
  last_best_end_date = best_end_date,
  last_ccr_as_of = ccr_as_of,
  --
  operation_no = :operation_no,
  total_touch_time = :total_touch_time,
  pre_ccr_buffer = :pre_ccr_buffer,
  post_ccr_buffer = :post_ccr_buffer,
  best_end_date = :best_end_date,
  ccr_as_of = :ccr_as_of,
  rowversion = sysdate
where contract = :contract
  and part_no = :part_no
  and bom_type_db = :bom_type_db
  and routing_revision_no = :routing_revision_no
  and routing_alternative_no = :routing_alternative_no
