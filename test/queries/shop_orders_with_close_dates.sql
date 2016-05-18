select
  so.order_no,
  so.release_no release,
  so.sequence_no sequence,
  so.close_date
from ifsapp.shop_ord so
where so.objstate = 'Closed'
