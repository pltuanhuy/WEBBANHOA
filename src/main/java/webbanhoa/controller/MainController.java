package webbanhoa.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import webbanhoa.dao.*;
import webbanhoa.entity.*;
import webbanhoa.model.*;
import webbanhoa.util.*;
import webbanhoa.validator.*;

@Controller
//Cần thiết cho Hibernate Transaction.
@Transactional
//Cần thiết để sử dụng RedirectAttributes
@EnableWebMvc
public class MainController {
	@Autowired
	private OrderDAO orderDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private CustomerInfoValidator customerInfoValidator;

	@InitBinder
	public void myInitBinder(WebDataBinder dataBinder) {
		Object target = dataBinder.getTarget();
		if (target == null) {
			return;
		}
		System.out.println("Target=" + target);

		// Trường hợp update SL trên giỏ hàng.
		// (@ModelAttribute("cartForm") @Validated CartInfo cartForm)
		if (target.getClass() == CartInfo.class) {

		}

		// Trường hợp save thông tin khách hàng.
		// (@ModelAttribute("customerForm") @Validated CustomerInfo
		// customerForm)
		else if (target.getClass() == CustomerInfo.class) {
			dataBinder.setValidator(customerInfoValidator);
		}

	}

	@RequestMapping("/403")
	public String accessDenied() {
		return "/403";
	}

	@RequestMapping("/")
	public String home() {
		return "index";
	}

	// Danh sách sản phẩm.
	@RequestMapping({ "/productList" })
	public String listProductHandler(Model model, //
			@RequestParam(value = "name", defaultValue = "") String likeName,
			@RequestParam(value = "page", defaultValue = "1") int page) {
		final int maxResult = 5;
		final int maxNavigationPage = 10;

		PaginationResult<ProductInfo> result = productDAO.queryProducts(page, //
				maxResult, maxNavigationPage, likeName);

		model.addAttribute("paginationProducts", result);
		return "productList";
	}

	@RequestMapping({ "/buyProduct" })
	public String listProductHandler(HttpServletRequest request, Model model, //
			@RequestParam(value = "code", defaultValue = "") String code) {

		Product product = null;
		if (code != null && code.length() > 0) {
			product = productDAO.findProduct(code);
		}
		if (product != null) {

			// Thông tin giỏ hàng có thể đã lưu vào trong Session trước đó.
			CartInfo cartInfo = Utils.getCartInSession(request);

			ProductInfo productInfo = new ProductInfo(product);

			cartInfo.addProduct(productInfo, 1);
		}

		// Chuyển sang trang danh sách các sản phẩm đã mua.
		return "redirect:/shoppingCart";
	}

	@RequestMapping({ "/shoppingCartRemoveProduct" })
	public String removeProductHandler(HttpServletRequest request, Model model, //
			@RequestParam(value = "code", defaultValue = "") String code) {
		Product product = null;
		if (code != null && code.length() > 0) {
			product = productDAO.findProduct(code);
		}
		if (product != null) {

			// Thông tin giỏ hàng có thể đã lưu vào trong Session trước đó.
			CartInfo cartInfo = Utils.getCartInSession(request);

			ProductInfo productInfo = new ProductInfo(product);

			cartInfo.removeProduct(productInfo);

		}

		// Chuyển sang trang danh sách các sản phẩm đã mua.
		return "redirect:/shoppingCart";
	}

	// POST: Cập nhập số lượng cho các sản phẩm đã mua.
	@RequestMapping(value = { "/shoppingCart" }, method = RequestMethod.POST)
	public String shoppingCartUpdateQty(HttpServletRequest request, //
			Model model, //
			@ModelAttribute("cartForm") CartInfo cartForm) {

		CartInfo cartInfo = Utils.getCartInSession(request);
		cartInfo.updateQuantity(cartForm);

		// Chuyển sang trang danh sách các sản phẩm đã mua.
		return "redirect:/shoppingCart";
	}

	// GET: Hiển thị giỏ hàng.
	@RequestMapping(value = { "/shoppingCart" }, method = RequestMethod.GET)
	public String shoppingCartHandler(HttpServletRequest request, Model model) {
		CartInfo myCart = Utils.getCartInSession(request);

		model.addAttribute("cartForm", myCart);
		return "shoppingCart";
	}

	// GET: Nhập thông tin khách hàng.
	@RequestMapping(value = { "/shoppingCartCustomer" }, method = RequestMethod.GET)
	public String shoppingCartCustomerForm(HttpServletRequest request, Model model) {

		CartInfo cartInfo = Utils.getCartInSession(request);

		// Chưa mua mặt hàng nào.
		if (cartInfo.isEmpty()) {

			// Chuyển tới trang danh giỏ hàng
			return "redirect:/shoppingCart";
		}

		CustomerInfo customerInfo = cartInfo.getCustomerInfo();
		if (customerInfo == null) {
			customerInfo = new CustomerInfo();
		}

		model.addAttribute("customerForm", customerInfo);

		return "shoppingCartCustomer";
	}

	// POST: Save thông tin khách hàng.
	@RequestMapping(value = { "/shoppingCartCustomer" }, method = RequestMethod.POST)
	public String shoppingCartCustomerSave(HttpServletRequest request, //
			Model model, //
			@ModelAttribute("customerForm") @Validated CustomerInfo customerForm, //
			BindingResult result, //
			final RedirectAttributes redirectAttributes) {

		// Kết quả Validate CustomerInfo.
		if (result.hasErrors()) {
			customerForm.setValid(false);
			// Forward to reenter customer info.
			// Forward tới trang nhập lại.
			return "shoppingCartCustomer";
		}

		customerForm.setValid(true);
		CartInfo cartInfo = Utils.getCartInSession(request);

		cartInfo.setCustomerInfo(customerForm);

		// Chuyển hướng sang trang xác nhận.
		return "redirect:/shoppingCartConfirmation";
	}

	// GET: Xem lại thông tin để xác nhận.
	@RequestMapping(value = { "/shoppingCartConfirmation" }, method = RequestMethod.GET)
	public String shoppingCartConfirmationReview(HttpServletRequest request, Model model) {
		CartInfo cartInfo = Utils.getCartInSession(request);

		// Chưa mua mặt hàng nào.
		if (cartInfo.isEmpty()) {

			// Chuyển tới trang danh giỏ hàng
			return "redirect:/shoppingCart";
		} else if (!cartInfo.isValidCustomer()) {

			// Chuyển tới trang nhập thông tin khách hàng.
			return "redirect:/shoppingCartCustomer";
		}

		return "shoppingCartConfirmation";
	}

	// POST: Gửi đơn hàng (Save).
	@RequestMapping(value = { "/shoppingCartConfirmation" }, method = RequestMethod.POST)

	// Tránh ngoại lệ: UnexpectedRollbackException (Xem giải thích thêm).
	@Transactional(propagation = Propagation.NEVER)
	public String shoppingCartConfirmationSave(HttpServletRequest request, Model model) {
		CartInfo cartInfo = Utils.getCartInSession(request);

		// Chưa mua mặt hàng nào.
		if (cartInfo.isEmpty()) {

			// Chuyển tới trang danh giỏ hàng
			return "redirect:/shoppingCart";
		} else if (!cartInfo.isValidCustomer()) {

			// Chuyển tới trang nhập thông tin khách hàng.
			return "redirect:/shoppingCartCustomer";
		}
		try {
			orderDAO.saveOrder(cartInfo);
		} catch (Exception e) {

			// Cần thiết: Propagation.NEVER?
			return "shoppingCartConfirmation";
		}

		// Xóa rỏ hàng khỏi session.
		Utils.removeCartInSession(request);

		// Lưu thông tin đơn hàng đã xác nhận mua.
		Utils.storeLastOrderedCartInSession(request, cartInfo);

		// Chuyến hướng tới trang hoàn thành mua hàng.
		return "redirect:/shoppingCartFinalize";
	}

	@RequestMapping(value = { "/shoppingCartFinalize" }, method = RequestMethod.GET)
	public String shoppingCartFinalize(HttpServletRequest request, Model model) {

		CartInfo lastOrderedCart = Utils.getLastOrderedCartInSession(request);

		if (lastOrderedCart == null) {
			return "redirect:/shoppingCart";
		}

		return "shoppingCartFinalize";
	}

	@RequestMapping(value = { "/productImage" }, method = RequestMethod.GET)
	public void productImage(HttpServletRequest request, HttpServletResponse response, Model model,
			@RequestParam("code") String code) throws IOException {
		Product product = null;
		if (code != null) {
			product = this.productDAO.findProduct(code);
		}
		if (product != null && product.getImage() != null) {
			response.setContentType("image/jpeg, image/jpg, image/png, image/gif");
			response.getOutputStream().write(product.getImage());
		}
		response.getOutputStream().close();
	}
}
