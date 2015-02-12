select
  ifsapp.work_time_calendar_api.get_end_date(
    ifsapp.site_api.get_manuf_calendar_id(:contract),
    nvl(:start_date, trunc(sysdate)),
    :post_ccr_buffer
  ) best_end_date
from dual
