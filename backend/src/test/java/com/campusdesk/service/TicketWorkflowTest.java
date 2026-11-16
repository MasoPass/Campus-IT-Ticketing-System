package com.campusdesk.service;

import com.campusdesk.model.TicketStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.campusdesk.model.Role.ADMIN;
import static com.campusdesk.model.Role.STUDENT;
import static com.campusdesk.model.Role.TECHNICIAN;
import static com.campusdesk.model.TicketStatus.ASSIGNED;
import static com.campusdesk.model.TicketStatus.CLOSED;
import static com.campusdesk.model.TicketStatus.IN_PROGRESS;
import static com.campusdesk.model.TicketStatus.NEW;
import static com.campusdesk.model.TicketStatus.RESOLVED;
import static com.campusdesk.model.TicketStatus.WAITING_ON_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketWorkflowTest {

    @Test
    void nobodyPicksAssignedDirectly() {
        for (TicketStatus from : TicketStatus.values()) {
            assertFalse(TicketWorkflow.canMove(from, ASSIGNED, ADMIN, false, false));
        }
    }

    @Test
    void assigneeWorksTheTicket() {
        assertEquals(Set.of(IN_PROGRESS, WAITING_ON_USER, RESOLVED),
                TicketWorkflow.allowedFor(ASSIGNED, TECHNICIAN, false, true));
        assertTrue(TicketWorkflow.canMove(RESOLVED, CLOSED, TECHNICIAN, false, true));
    }

    @Test
    void otherTechniciansCantTouchSomeoneElsesTicket() {
        assertEquals(Set.of(), TicketWorkflow.allowedFor(IN_PROGRESS, TECHNICIAN, false, false));
        assertFalse(TicketWorkflow.canMove(RESOLVED, CLOSED, TECHNICIAN, false, false));
    }

    @Test
    void technicianCanPickUpANewTicketByStartingIt() {
        assertEquals(Set.of(IN_PROGRESS), TicketWorkflow.allowedFor(NEW, TECHNICIAN, false, false));
    }

    @Test
    void studentCanConfirmReopenOrWithdrawOwnTicketOnly() {
        assertEquals(Set.of(CLOSED, IN_PROGRESS), TicketWorkflow.allowedFor(RESOLVED, STUDENT, true, false));
        assertEquals(Set.of(CLOSED), TicketWorkflow.allowedFor(NEW, STUDENT, true, false));
        assertEquals(Set.of(), TicketWorkflow.allowedFor(IN_PROGRESS, STUDENT, true, false));
        assertEquals(Set.of(), TicketWorkflow.allowedFor(RESOLVED, STUDENT, false, false)); // not theirs
    }

    @Test
    void onlyAdminsReopenClosedTickets() {
        assertTrue(TicketWorkflow.canMove(CLOSED, IN_PROGRESS, ADMIN, false, false));
        assertFalse(TicketWorkflow.canMove(CLOSED, IN_PROGRESS, TECHNICIAN, false, true));
        assertFalse(TicketWorkflow.canMove(CLOSED, IN_PROGRESS, STUDENT, true, false));
    }

    @Test
    void resolvingOrClosingEarlyNeedsAnExplanation() {
        assertTrue(TicketWorkflow.requiresResolution(IN_PROGRESS, RESOLVED));
        assertTrue(TicketWorkflow.requiresResolution(NEW, CLOSED));
        assertFalse(TicketWorkflow.requiresResolution(RESOLVED, CLOSED));
        assertFalse(TicketWorkflow.requiresResolution(ASSIGNED, IN_PROGRESS));
    }
}
