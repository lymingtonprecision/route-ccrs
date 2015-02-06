select
  ccr.contract,
  ccr.operation_no,
  ccr.work_center_no,
  ccr.total_touch_time,
  ccr.pre_ccr_buffer,
  ccr.post_ccr_buffer,
  ccr.best_end_date,
  ccr.ccr_as_of
from routing_ccr ccr
where ccr.contract = :contract
  and ccr.part_no = :part_no
  and ccr.bom_type_db = :bom_type_db
  and ccr.routing_revision_no = :routing_revision_no
  and ccr.routing_alternative_no = :routing_alternative_no
