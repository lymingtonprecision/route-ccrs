alter table routing_ccr_hist
  modify operation_no null
  modify work_center_no null
  modify pre_ccr_buffer null
  modify post_ccr_buffer null
;

alter table routing_ccr
  modify operation_no null
  modify work_center_no null
  modify pre_ccr_buffer null
  modify post_ccr_buffer null
;
