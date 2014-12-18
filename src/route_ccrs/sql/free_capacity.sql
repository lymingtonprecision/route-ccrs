select
  fc.contract,
  fc.work_center_no,
  fc.start_work_day,
  fc.finish_work_day,
  fc.capacity_available
from finiteload.free_capacity fc
order by
  fc.work_center_no,
  fc.start_work_day
