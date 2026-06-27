package io.binarycodes.calculators.irr.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The inputs for the IRR/XIRR calculator: money paid in (investments /
 * premiums) and money received (withdrawals / returns), each split into one-off
 * dated entries and recurring schedules. Amounts are stored as positive
 * magnitudes; the calculator assigns signs (investments negative, withdrawals
 * positive) when it expands the schedule.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class XirrInputs {
    private List<DatedCashflow> oneOffInvestments = new ArrayList<>();
    private List<RecurringCashflow> recurringInvestments = new ArrayList<>();
    private List<DatedCashflow> oneOffWithdrawals = new ArrayList<>();
    private List<RecurringCashflow> recurringWithdrawals = new ArrayList<>();
}
