select
  min(
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      ifsapp.work_time_calendar_api.get_end_time(
        wc.calendar_id,
        greatest(
          fc.start_work_day,
          ifsapp.work_time_calendar_api.get_end_date(
            wc.calendar_id,
            trunc(sysdate),
            :pre_ccr_buffer
          )
        ),
        :total_touch_time
      ),
      :post_ccr_buffer
    )
  ) best_end_date
from finiteload.free_capacity fc
join ifsapp.work_center wc
  on fc.contract = wc.contract
  and fc.work_center_no = wc.work_center_no
where fc.contract = :contract
  and fc.work_center_no = :work_center_no
  and fc.capacity_available >= :total_touch_time
  and fc.finish_work_day >=
    ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      trunc(sysdate),
      :pre_ccr_buffer
    )
  and ifsapp.work_time_calendar_api.get_end_date(
      wc.calendar_id,
      trunc(sysdate),
      :pre_ccr_buffer
    ) <=
    ifsapp.work_time_calendar_api.get_start_time(
      wc.calendar_id,
      fc.finish_work_day,
      :total_touch_time
    )
