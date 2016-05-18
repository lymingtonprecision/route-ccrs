select
  decode(
    so.objstate,
    'Closed', so.close_date,
    so.revised_due_date
  ) end_date
from ifsapp.shop_ord so
where order_no = :order_no
  and release_no = :release
  and sequence_no = :sequence
