insert into routing_ccr_hist values (
  :contract,
  :part_no,
  :bom_type_db,
  :routing_revision_no,
  :routing_alternative_no,
  :operation_no,
  :work_center_no,
  :total_touch_time,
  :pre_ccr_buffer,
  :post_ccr_buffer,
  :best_end_date,
  :calculated_at,
  sysdate
)
