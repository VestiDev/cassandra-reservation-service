/*
 * Copyright (C) 2017 Jeff Carpenter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cassandraguide.services.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

// DataStax Java Driver imports
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;

// TODO: add imports for QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;

@Component
public class ReservationService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

    // private variable to hold DataStax Java Driver Session - used for executing queries
    private Session session;

    public ReservationService() {

        // Create cluster with connection to localhost
        Cluster cluster = Cluster.builder()
                .addContactPoint("127.0.0.1")
                .build();

        // Create session for reservation keyspace
        session = cluster.connect("reservation");

    }

    public String createReservation(Reservation reservation) {


        /*
         * Business Logic -
         *  you should not need to change this code, although in a real production service
         *  you'd likely do more validation than is done here
         */

        // Validate new reservation doesn't contain a confirmation number
        if (reservation.getConfirmationNumber() != null)
        {
            logger.error("Received new reservation containing confirmation number: " +
                reservation.getConfirmationNumber());
            throw new IllegalArgumentException("New reservation cannot contain confirmation number");
        }

        // generate and set confirmation number
        reservation.setConfirmationNumber(getUniqueConfirmationNumber());

        /*
         * Data Manipulation Logic
         */
        Statement reservationsByConfirmationInsert = null;

        // TODO: use QueryBuilder to create a BuiltStatement for inserting the reservation
        // For this exercise we will insert only into the reservations_by_confirmation table
        // Hint: use provided convenience function convertJavaLocalDateToDataStax for start and end dates
        reservationsByConfirmationInsert = QueryBuilder.insertInto("reservations_by_confirmation")
                .value("confirmation_number", reservation.getConfirmationNumber())
                .value("hotel_id", reservation.getHotelId())
                .value("start_date", convertJavaLocalDateToDataStax(reservation.getStartDate()))
                .value("end_date", convertJavaLocalDateToDataStax(reservation.getEndDate()))
                .value("room_number", reservation.getRoomNumber())
                .value("guest_id", reservation.getGuestId());

        // Execute the statement
        session.execute(reservationsByConfirmationInsert);

        // Return the confirmation number that was created
        return reservation.getConfirmationNumber();
    }

    public Reservation retrieveReservation(String confirmationNumber) {

        Reservation reservation = null;

        /*
         * Data Manipulation Logic
         */
        Statement reservationsByConfirmationSelect = null;

        // TODO: use QueryBuilder to create a BuiltStatement for retrieving the reservation
        // from the reservations_by_confirmation table
        reservationsByConfirmationSelect = QueryBuilder.select().from("reservations_by_confirmation")
            .where(eq("confirmation_number", confirmationNumber));

        // Execute the statement
        ResultSet resultSet = session.execute(reservationsByConfirmationSelect);
        Row row = resultSet.one();

        // Process the results (ResultSet)
        // Hint: an empty result might not be an error as this method is sometimes used to check whether a
        // reservation with this confirmation number exists
        if (row == null) {
            logger.debug("Unable to load reservation with confirmation number: " + confirmationNumber);
        }
        else {
            reservation = extractReservationFromRow(row);
        }

        return reservation;
    }

    public void updateReservation(Reservation reservation) {
        /*
         * Business Logic -
         *  you should not need to change this code, although in a real production service
         *  you'd likely do more validation than is done here
         */

        // verify reservation exists
        if (retrieveReservation(reservation.getConfirmationNumber()) == null)
        {
            String errorMsg = "Unable to update unknown reservation with confirmation number: " +
                    reservation.getConfirmationNumber();
            logger.error(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        /*
         * Data Manipulation Logic
         */
        Statement reservationsByConfirmationUpdate = null;

        // TODO: use QueryBuilder to create a BuiltStatement for updating the reservation
        // For this exercise we will insert only into the reservations_by_confirmation table
        // Hint: use provided convenience function convertJavaLocalDateToDataStax for start and end dates
        reservationsByConfirmationUpdate  = QueryBuilder.update("reservations_by_confirmation")
                .with(set("hotel_id", reservation.getHotelId()))
                .and(set("start_date", convertJavaLocalDateToDataStax(reservation.getStartDate())))
                .and(set("end_date", convertJavaLocalDateToDataStax(reservation.getEndDate())))
                .and(set("room_number", reservation.getRoomNumber()))
                .and(set("guest_id", reservation.getGuestId()))
                .where(eq("confirmation_number", reservation.getConfirmationNumber()));

        // Execute the statement
        session.execute(reservationsByConfirmationUpdate);
    }

    public List<Reservation> getAllReservations() {

        // for populating return value
        List<Reservation> reservations = new ArrayList<Reservation>();

        /*
         * Data Manipulation Logic
         */
        Statement reservationsByConfirmationSelectAll = null;

        // TODO: use QueryBuilder to create a BuiltStatement for retrieving all reservations
        // from the reservations_by_confirmation table
        // Hint: there are no parameters to pass to bind
        reservationsByConfirmationSelectAll = QueryBuilder.select().from("reservations_by_confirmation");

        // Execute the statement to get a result set
        ResultSet resultSet = session.execute(reservationsByConfirmationSelectAll);

        // Iterate over the rows in the result set, creating a reservation for each one
        for (Row row : resultSet) {
            reservations.add(extractReservationFromRow(row));
        }

        return reservations;
    }

    public List<Reservation> searchReservationsByHotelDate(String hotelId, LocalDate date) {

        // We will implement this in a later exercise
        throw new UnsupportedOperationException();
    }

    public void deleteReservation(String confirmationNumber) {
        /*
         * Data Manipulation Logic
         */
        Statement reservationsByConfirmationDelete = null;

        // TODO: use QueryBuilder to create a BuiltStatement for deleting the reservation
        // from the reservations_by_confirmation table
        reservationsByConfirmationDelete = QueryBuilder.delete().from("reservations_by_confirmation")
                .where(eq("confirmation_number", confirmationNumber));

        // Execute the statement
        session.execute(reservationsByConfirmationDelete);
    }

    // convenience method, you should not need to modify
    private String getUniqueConfirmationNumber()
    {
        String confirmationNumber;

        // Ensure uniqueness of the generated code by searching for a reservation with that code
        do {
            confirmationNumber = ConfirmationNumberGenerator.getConfirmationNumber();
        }
        while (retrieveReservation(confirmationNumber) != null);

        return confirmationNumber;
    }

    // convenience method to be replaced in a later exercise by codecs
    private com.datastax.driver.core.LocalDate convertJavaLocalDateToDataStax(java.time.LocalDate date)
    {
        if (date == null) return null;
        int year=date.getYear();
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        return com.datastax.driver.core.LocalDate.fromYearMonthDay(year, month, day);
    }

    // convenience method to be replaced in a later exercise by codecs
    private java.time.LocalDate convertDataStaxLocalDateToJava(com.datastax.driver.core.LocalDate date)
    {
        if (date == null) return null;
        return java.time.LocalDate.parse(date.toString());
    }

    // method to extract a Reservation from a row
    private Reservation extractReservationFromRow(Row row) {
        Reservation reservation;
        reservation = new Reservation();
        reservation.setConfirmationNumber(row.getString("confirmation_number"));
        reservation.setHotelId(row.getString("hotel_id"));
        reservation.setStartDate(convertDataStaxLocalDateToJava(row.getDate("start_date")));
        reservation.setEndDate(convertDataStaxLocalDateToJava(row.getDate("end_date")));
        reservation.setGuestId(row.getUUID("guest_id"));
        reservation.setRoomNumber(row.getShort("room_number"));
        return reservation;
    }
}