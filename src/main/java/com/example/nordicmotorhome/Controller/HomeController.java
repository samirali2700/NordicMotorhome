package com.example.nordicmotorhome.Controller;

import com.example.nordicmotorhome.Admin;
import com.example.nordicmotorhome.Model.Booking;
import com.example.nordicmotorhome.Model.Invoice;
import com.example.nordicmotorhome.Model.MotorHome;
import com.example.nordicmotorhome.Model.Renter;
import com.example.nordicmotorhome.Service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.tags.form.RadioButtonTag;

import java.util.ArrayList;


@Controller
public class HomeController {
    @Autowired
    BookingService bookingService;
    @Autowired
    RenterService renterService;
    @Autowired
    MotorHomeService motorHomeService;
    @Autowired
    AdminService adminService;
    @Autowired
    InvoiceService invoiceService;

    /*******************************    Homepage     *******************************/
    @GetMapping("/")
    public String index(){
        return "home/index";
    }

    /*******************************    Booking     *******************************/
    @GetMapping("/bookings")
    public String bookingPage(Model model){
        ArrayList<Booking> list =(ArrayList<Booking>) bookingService.fetchAll();
        model.addAttribute("bookings",list);

        return "home/Booking/bookingsPage";
    }
    @GetMapping("/addBooking")
    public String addBookingPage(Model model){
        ArrayList<Renter> renterList = (ArrayList<Renter>) renterService.fetchAll();
        ArrayList<Booking> bookingList = (ArrayList<Booking>) bookingService.fetchAll();

        //removing every booked renter from list
        for(Booking b : bookingList){
            renterList.removeIf(r -> r.getRenter_ID() == b.getRenter_ID());
        }
        //if list is empty after removal
        if(renterList.isEmpty()){
            return "home/Booking/allBooked";
        }
        model.addAttribute("renter",renterList);

        return "home/Booking/addBooking";

    }
    @GetMapping("/pickRenter/{renter_ID}")
    public String pickRenter(@PathVariable("renter_ID") int renterID, Model model){
        ArrayList<MotorHome> motorList = (ArrayList<MotorHome>) motorHomeService.fetchAll();
        ArrayList<Booking> bookingList = (ArrayList<Booking>) bookingService.fetchAll();

        //removing booked motorhomes
        for(Booking b : bookingList){
            motorList.removeIf(m -> m.getMotorhome_ID() == b.getMotorhome_ID());
        }

        model.addAttribute("assignedRenter",renterService.fetchById(renterID));
        model.addAttribute("motors",motorList);
        return "home/Booking/addBookingAssignMotorhome";
    }
    @GetMapping("/addBookingConfirm/{renter_ID}/{motorhome_ID}")
    public String confirmBooking(@PathVariable("renter_ID") int renterID, @PathVariable("motorhome_ID") int motorID, Model model){
        model.addAttribute("assignedRenter", renterService.fetchById(renterID));
        model.addAttribute("assignedMotor",motorHomeService.fetchById(motorID));

        return "home/Booking/bookingDetails";
    }
    @PostMapping("/confirmBookingDetails/{renter_ID}/{motorhome_ID}")
    public String confirmBookingDetails(@PathVariable("renter_ID") int renterID,
                                        @PathVariable("motorhome_ID") int motorID,
                                        @ModelAttribute Booking booking, Model model){


        Admin admin = adminService.fetchPrice();
        //defines startKM an stores in booking object
        booking.setStart_km(motorHomeService.fetchById(motorID).getKm());

        //Sets total Of Days
        booking.setDaysTotal(bookingService.getTotalDays(booking.getPickup_date(),booking.getReturn_date()));

        //Booking instance is added to DB
        bookingService.addBooking(booking);
        bookingService.setBookingStatus();

        //booking Object is updated with a bookingID
        booking = bookingService.fetchByRenterID(renterID);


        //Invoice Section

        //General Fields that always needs to be done before invoice,to make sure all prices are updated
        //Extra Equipments, Price pr equimpent is stored in Admin Object * amount of extra items in booking
        int extra = admin.getExtraPrice()*booking.getExtras();

        //Set BasePrice from Admin Object
        double price = admin.getBasePrice();

        //For outside pickup & dropoff, a fee for each km to distination
        double outsideKmFee = booking.getTotalKm()*admin.getCollectFee();

        //seasonal percentage is defined with pickup_date
        int season_percent = adminService.getPrice_percent(booking.getPickup_date());

        Invoice invoice = new Invoice(booking.getBooking_ID(),season_percent,price,extra,outsideKmFee);
        invoice.updateInvoice(season_percent,admin,booking);
        invoiceService.addInvoice(invoice);
        return "redirect:/bookings";
    }
    @GetMapping("/updateBooking/{booking_ID}")
    public String updateBookingPage(@PathVariable("booking_ID") int bookingID,Model model){
        model.addAttribute("booking",bookingService.fetchById(bookingID));
        return "home/Booking/updateBooking";
    }
    @PostMapping("/saveUpdate")
    public String saveUpdate(@ModelAttribute Booking booking){
        Admin admin = adminService.fetchPrice();

        //updating daysTotal incase pickup and drop are changed
        booking.setDaysTotal(bookingService.getTotalDays(booking.getPickup_date(),booking.getReturn_date()));

        //fetching the associated invoice via booking ID
        Invoice invoice = invoiceService.fetchByID(booking.getBooking_ID());

        //Invoice fields are updated with method updateInvoice()
        invoice.updateInvoice(adminService.getPrice_percent(booking.getPickup_date()), admin, booking);
        invoiceService.updateInvoice(invoice);
        bookingService.updateBooking(booking);

        return "redirect:/bookings";
    }
    @GetMapping("/deleteBooking{booking_ID}")
    public String deleteBooking(@PathVariable("booking_ID") int bookingID){
        bookingService.deleteBooking(bookingID);
        return "redirect:/adminBooking";
    }

    //Not DOne
    @GetMapping("/cancelBooking{booking_ID}")
    public String cancelBooking(@PathVariable("booking_ID") int bookingID ){
        int cancelFee = adminService.getCancellationPercent(bookingID);
        Invoice invoice = invoiceService.fetchByID(bookingID);
        double price = invoice.bookingCancel();

        bookingService.cancelBooking(bookingID);
        System.out.println(price+" - "+cancelFee+" %");

        price = (price*((double)cancelFee/100));
        invoice.setPrice(price);
        invoiceService.updateInvoice(invoice);

        return "redirect:/bookings";
    }

    /*******************************    Invoice     ********************************/
    @GetMapping("/invoice/{booking_ID}")
    public String invoice(@PathVariable("booking_ID") int bookingID, Model model){
        Booking booking = bookingService.fetchById(bookingID);;
        Invoice invoice = invoiceService.fetchByID(bookingID);
        model.addAttribute("booking",booking);

        if(!booking.getStatus().equals("canceled")) {
            invoice.updateInvoice(adminService.getPrice_percent(booking.getPickup_date()), adminService.fetchPrice(), booking);
            invoiceService.updateInvoice(invoice);
        }

        model.addAttribute("renter",renterService.fetchById(booking.getRenter_ID()));
        model.addAttribute("invoice",invoice);


        return "home/Invoice/invoice";
    }
    @GetMapping("/allInvoices")
    public String allInvoices(Model model){

        ArrayList<Invoice> list = (ArrayList<Invoice>) invoiceService.fetchAll();
        model.addAttribute("invoice",list);

        return "home/Invoice/invoices";
    }
    @GetMapping("/generateInvoice{booking_ID}")
    public String generateInvoice(@PathVariable("booking_ID") int booking_ID, Model model){

        Booking booking = bookingService.fetchById(booking_ID);
        //price
        Admin admin = adminService.fetchPrice();
        int ex = admin.getExtraPrice()*booking.getExtras();
        int ol = booking.getKmToPickup()+booking.getKmToDropoff();



        //define season percent for booking


        return "redirect:/invoice/{booking_ID}";
    }
    /*******************************    Renter     *******************************/
    @GetMapping("/renter")
    public String renterPage(Model model){
        ArrayList<Renter> list =(ArrayList<Renter>) renterService.fetchAll();
        model.addAttribute("renters",list);

        return "home/Renter/rentersPage";
    }
    @GetMapping("/addRenter")
    public String addRenterPage(){ return "home/Renter/addRenter"; }
    @PostMapping("/add/")
    public String add(@ModelAttribute Renter r, @RequestParam(value="enableBooking") String choice, RedirectAttributes rd){
        int id = renterService.addRenter(r);

        System.out.println(r.getMobile_number());
        if(choice.equals("yes")){
            rd.addAttribute("renter_ID",id);
            return "redirect:/pickRenter/{renter_ID}";
        }



        return "redirect:/renter";
    }
    @GetMapping("/updateRenter/{renter_ID}")
    public String updateRenterPage(@PathVariable("renter_ID") int renterID, Model model){
        model.addAttribute("renter",renterService.fetchById(renterID));
        return "home/Renter/updateRenter";
    }
    @PostMapping("/updateRenter/")
    public String updateRenter(@ModelAttribute Renter r){
        renterService.updateRenter(r);
        return "redirect:/renter";
    }
    @GetMapping("/deleteRenter/{renter_ID}")
    public String deleteRenterPage(@PathVariable("renter_ID") int id){
        renterService.deleteRenter(id);
        return "redirect:/renter";
    }


    /*******************************    Motorhome     *******************************/
    @GetMapping("/motorhomes")
    public String MotorHomePage(Model model){
        ArrayList<MotorHome> list =(ArrayList<MotorHome>) motorHomeService.fetchAll();
        model.addAttribute("MotorHomes",list);
        return "home/MotorHome/MotorHomePage";
    }
    @GetMapping("/addMotorHome")
    public String addMotorHomePage(){ return "home/MotorHome/addMotorHome"; }
    @GetMapping("/updateMotorHome")
    public String updateMotorHomePage(){ return "home/MotorHome/updateMotorHome"; }

    /*******************************    Admin   ************************************/

    @GetMapping("/admin")
    public String adminPage(Model model){
        model.addAttribute("renterCount",renterService.renterCount());
        model.addAttribute("motorCount",motorHomeService.motorhomeCount());
        model.addAttribute("bookingCount",bookingService.bookingCount());
        model.addAttribute("staffCount",adminService.staffCount());
        model.addAttribute("seasonName", adminService.getSeasonName());
        model.addAttribute("price",adminService.fetchPrice());
        String percent = adminService.getCurrentPricePercent()+" %";
        model.addAttribute("pricePercent",percent);

        return "home/Admin/ownerPage";
    }
    @GetMapping("/adminRenter")
    public String adminRenter(Model model){
        ArrayList<Renter> list = (ArrayList<Renter>) renterService.fetchAll();
        model.addAttribute("renters",list);

        return "home/Admin/rentersView";
    }
    @GetMapping("/adminBooking")
    public String adminBooking(Model model){
        ArrayList<Booking> list = (ArrayList<Booking>) bookingService.fetchAll();
        model.addAttribute("bookings",list);
        return "home/Admin/Booking/bookingsView";
    }

    //Invoice
    @GetMapping("/adminInvoice")
    public String adminInvoice(Model model){
        ArrayList<Invoice> list = (ArrayList<Invoice>) invoiceService.fetchAll();
        model.addAttribute("invoice",list);

        return "home/Admin/Invoice/invoiceView";
    }
    @GetMapping("/showInvoice/{booking_ID}")
    public String showInvoice(@PathVariable("booking_ID") int bookingID, Model model){

        Booking booking = bookingService.fetchById(bookingID);;
        Invoice invoice = invoiceService.fetchByID(bookingID);

        model.addAttribute("invoice",invoice);
        model.addAttribute("renter",renterService.fetchById(booking.getRenter_ID()));
        model.addAttribute("booking",booking);

        return "home/Admin/Invoice/showInvoice";
    }
    @GetMapping("/deleteInvoice/{invoice_ID}")
    public String deleteInvoice(@PathVariable("invoice_ID") int invoiceID){
        invoiceService.deleteInvoice(invoiceID);

        return "redirect:/adminInvoice";
    }

    //Pricing
    @GetMapping("/adminPricing")
    public String adminPricing(Model model){
        ArrayList<Admin> seasonList = (ArrayList<Admin>) adminService.fetchSeasons();
        ArrayList<Admin> cancelList = (ArrayList<Admin>) adminService.fetchCancellation();

        model.addAttribute("cancellation",cancelList);
        model.addAttribute("price",adminService.fetchPrice());
        model.addAttribute("season",seasonList);

        return "home/Admin/Pricing/pricingView";
    }
    @GetMapping("/savePricing")
    public String savePricing(@ModelAttribute Admin admin){
            adminService.updatePrice(admin);

        //update DB
        return "redirect:/adminPricing";
    }

    //Seasons
    @GetMapping("/updateSeason{season_name}")
    public String updateSeason(@PathVariable("season_name") String season_name, Model model){
            model.addAttribute("season",adminService.getSeasonsByName(season_name));
        return "home/Admin/Pricing/updateSeason";
    }
    @GetMapping("/saveSeason")
    public String saveSeason(@ModelAttribute Admin admin){
        adminService.updateSeason(admin);
        return "redirect:/adminPricing";
    }

    //cancellation
    @GetMapping("/saveCancellation/{cancellation_ID}")
    public String saveCancellation(@PathVariable("cancellation_ID") int id, @ModelAttribute Admin admin){
        System.out.println(admin.getCancellation_ID()+"\n"+admin.getMinPrice());
        adminService.updateCancellation(admin);
        return  "redirect:/adminPricing";
    }
}