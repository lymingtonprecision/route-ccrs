select
  so.order_no,
  so.release_no release,
  so.sequence_no sequence,
  so.revised_due_date due_date
from ifsapp.shop_ord so
where so.objstate not in ('Closed', 'Cancelled')
