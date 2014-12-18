select
  min(
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      greatest(fc.start_work_day, trunc(sysdate)),
      ((:total_touch_time + :post_ccr_buffer) / 60 / wc.average_capacity)
    )
  ) best_end_date
from finiteload.free_capacity fc
join ifsapp.work_center wc
  on fc.contract = wc.contract
  and fc.work_center_no = wc.work_center_no
where fc.contract = :contract
  and fc.work_center_no = :work_center_no
  and (
    fc.start_work_day >= trunc(sysdate) or
    (fc.start_work_day < trunc(sysdate) and fc.finish_work_day >= trunc(sysdate))
  )
  and fc.capacity_available >= :total_touch_time
